#include "xiangqi/engine.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <sstream>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#else
#include <cerrno>
#include <csignal>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#endif

namespace xiangqi {
namespace {

std::vector<std::string> split_words(std::string_view line) {
    std::istringstream stream{std::string(line)};
    std::vector<std::string> words;
    std::string word;
    while (stream >> word) words.push_back(std::move(word));
    return words;
}

bool number(std::string_view text) {
    if (text.empty()) return false;
    std::size_t start = text.front() == '-' ? 1 : 0;
    if (start == text.size()) return false;
    return std::all_of(text.begin() + static_cast<std::ptrdiff_t>(start), text.end(), [](char c) {
        return std::isdigit(static_cast<unsigned char>(c)) != 0;
    });
}

} // namespace

EngineProtocol::EngineProtocol(std::string protocol) : protocol_(std::move(protocol)) {}

std::string EngineProtocol::option_command(std::string_view name, std::string_view value) const {
    if (protocol_ == "ucci") return "setoption " + std::string(name) + " " + std::string(value);
    return "setoption name " + std::string(name) + " value " + std::string(value);
}

bool EngineProtocol::validate_move(std::string_view move) {
    return move.size() == 4 && move[0] >= 'a' && move[0] <= 'i' && move[2] >= 'a' && move[2] <= 'i' &&
           move[1] >= '0' && move[1] <= '9' && move[3] >= '0' && move[3] <= '9';
}

std::optional<std::pair<std::string, std::optional<std::string>>>
EngineProtocol::parse_bestmove(std::string_view line) {
    const auto words = split_words(line);
    if (words.size() < 2 || words[0] != "bestmove" || !validate_move(words[1])) return std::nullopt;
    std::optional<std::string> ponder;
    for (std::size_t i = 2; i + 1 < words.size(); ++i) {
        if (words[i] == "ponder" && validate_move(words[i + 1])) {
            ponder = words[i + 1];
            break;
        }
    }
    return std::pair{words[1], ponder};
}

ThinkData EngineProtocol::parse_info(std::string_view line) {
    ThinkData data;
    const auto words = split_words(line);
    for (std::size_t i = 0; i < words.size(); ++i) {
        const auto set_int = [&](std::optional<int>& field) {
            if (i + 1 < words.size() && number(words[i + 1])) {
                field = std::stoi(words[++i]);
            }
        };
        const auto set_long = [&](std::optional<std::int64_t>& field) {
            if (i + 1 < words.size() && number(words[i + 1])) {
                field = std::stoll(words[++i]);
            }
        };
        if (words[i] == "depth") set_int(data.depth);
        else if (words[i] == "nps") set_long(data.nps);
        else if (words[i] == "time") set_long(data.time);
        else if (words[i] == "multipv") set_int(data.pv);
        else if (words[i] == "score" && i + 2 < words.size()) {
            if (words[i + 1] == "cp") {
                ++i;
                set_int(data.score);
            } else if (words[i + 1] == "mate") {
                ++i;
                set_int(data.mate);
            }
        } else if (words[i] == "mate") {
            set_int(data.mate);
        } else if (words[i] == "pv") {
            for (++i; i < words.size(); ++i) {
                if (validate_move(words[i])) data.detail.push_back(words[i]);
            }
            break;
        }
    }
    return data;
}

Engine::Engine(EngineConfig config, EngineCallbacks callbacks)
    : config_(std::move(config)), callbacks_(std::move(callbacks)), protocol_(config_.protocol) {}

Engine::~Engine() {
    close();
}

bool Engine::start() {
    if (running_) return true;
    if (config_.path.empty()) {
        if (callbacks_.error) callbacks_.error("引擎路径为空");
        return false;
    }

#ifdef _WIN32
    SECURITY_ATTRIBUTES security{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    HANDLE child_stdin_read = nullptr;
    HANDLE child_stdin_write = nullptr;
    HANDLE child_stdout_read = nullptr;
    HANDLE child_stdout_write = nullptr;
    if (!CreatePipe(&child_stdin_read, &child_stdin_write, &security, 0) ||
        !CreatePipe(&child_stdout_read, &child_stdout_write, &security, 0)) {
        if (callbacks_.error) callbacks_.error("创建引擎管道失败");
        return false;
    }
    SetHandleInformation(child_stdin_write, HANDLE_FLAG_INHERIT, 0);
    SetHandleInformation(child_stdout_read, HANDLE_FLAG_INHERIT, 0);
    STARTUPINFOA startup{};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdInput = child_stdin_read;
    startup.hStdOutput = child_stdout_write;
    startup.hStdError = child_stdout_write;
    PROCESS_INFORMATION process{};
    std::vector<char> command(config_.path.begin(), config_.path.end());
    command.push_back('\0');
    const BOOL created = CreateProcessA(nullptr, command.data(), nullptr, nullptr, TRUE,
                                        CREATE_NO_WINDOW, nullptr, nullptr, &startup, &process);
    CloseHandle(child_stdin_read);
    CloseHandle(child_stdout_write);
    if (!created) {
        CloseHandle(child_stdin_write);
        CloseHandle(child_stdout_read);
        if (callbacks_.error) callbacks_.error("启动引擎进程失败");
        return false;
    }
    CloseHandle(process.hThread);
    process_handle_ = process.hProcess;
    input_write_ = child_stdin_write;
    output_read_ = child_stdout_read;
#else
    int stdin_pipe[2]{};
    int stdout_pipe[2]{};
    if (pipe(stdin_pipe) != 0 || pipe(stdout_pipe) != 0) {
        if (callbacks_.error) callbacks_.error("创建引擎管道失败");
        return false;
    }
    process_id_ = fork();
    if (process_id_ == 0) {
        dup2(stdin_pipe[0], STDIN_FILENO);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stdout_pipe[1], STDERR_FILENO);
        close(stdin_pipe[0]); close(stdin_pipe[1]); close(stdout_pipe[0]); close(stdout_pipe[1]);
        execl("/bin/sh", "sh", "-c", config_.path.c_str(), static_cast<char*>(nullptr));
        _exit(127);
    }
    if (process_id_ < 0) {
        close(stdin_pipe[0]); close(stdin_pipe[1]); close(stdout_pipe[0]); close(stdout_pipe[1]);
        if (callbacks_.error) callbacks_.error("启动引擎进程失败");
        return false;
    }
    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    input_write_ = stdin_pipe[1];
    output_read_ = stdout_pipe[0];
#endif

    running_ = true;
    reader_thread_ = std::thread(&Engine::reader_loop, this);
    send(config_.protocol == "ucci" ? "ucci" : "uci");
    for (const auto& [name, value] : config_.options) send(protocol_.option_command(name, value));
    return true;
}

bool Engine::running() const noexcept {
    return running_;
}

bool Engine::send(std::string command) {
    {
        std::lock_guard lock(command_mutex_);
        commands_.push_back(command);
    }
    command.push_back('\n');
#ifdef _WIN32
    if (input_write_ != nullptr) {
        DWORD written = 0;
        if (!WriteFile(static_cast<HANDLE>(input_write_), command.data(),
                       static_cast<DWORD>(command.size()), &written, nullptr)) return false;
    }
#else
    if (input_write_ >= 0) {
        const auto* data = command.data();
        std::size_t remaining = command.size();
        while (remaining > 0) {
            const ssize_t written = ::write(input_write_, data, remaining);
            if (written < 0) {
                if (errno == EINTR) continue;
                return false;
            }
            data += written;
            remaining -= static_cast<std::size_t>(written);
        }
    }
#endif
    return true;
}

void Engine::reader_loop() {
    std::string line;
    char character = 0;
    while (running_) {
#ifdef _WIN32
        DWORD read = 0;
        if (output_read_ == nullptr || !ReadFile(static_cast<HANDLE>(output_read_), &character, 1, &read, nullptr) || read == 0) break;
#else
        const ssize_t read = ::read(output_read_, &character, 1);
        if (read <= 0) break;
#endif
        if (character == '\n') {
            consume_line(line);
            line.clear();
        } else if (character != '\r') {
            line += character;
        }
    }
    if (!line.empty()) consume_line(line);
}

void Engine::consume_line(std::string_view line) {
    if (line.find("bestmove") != std::string_view::npos) handle_bestmove(line);
    else if (line.find("depth") != std::string_view::npos || line.find("nps") != std::string_view::npos) handle_info(line);
}

void Engine::handle_bestmove(std::string_view line) {
    if (stop_flag_) {
        stop_flag_ = false;
        return;
    }
    if (pondering_ && !ponderhit_sent_) {
        pondering_ = false;
        ponderhit_sent_ = false;
        return;
    }
    pondering_ = false;
    const auto parsed = EngineProtocol::parse_bestmove(line);
    if (!parsed) return;
    if (callbacks_.best_move) {
        std::optional<std::string> ponder;
        if (parsed->second) ponder = *parsed->second;
        callbacks_.best_move(parsed->first, ponder);
    }
}

void Engine::handle_info(std::string_view line) {
    auto data = EngineProtocol::parse_info(line);
    if (data.depth && *data.depth < 5) stop_flag_ = false;
    if (!data.detail.empty() && callbacks_.think_detail) callbacks_.think_detail(data);
}

void Engine::analysis(std::string_view fen, const std::vector<std::string>& moves,
                      std::vector<std::string> tactic_list) {
    stop();
    if (thread_num_changed_) {
        send(protocol_.option_command("Threads", std::to_string(thread_num_)));
        thread_num_changed_ = false;
    }
    if (hash_size_changed_) {
        send(protocol_.option_command("Hash", std::to_string(hash_size_)));
        hash_size_changed_ = false;
    }
    std::string position = "position fen " + std::string(fen);
    if (!moves.empty()) {
        position += " moves";
        for (const auto& move : moves) position += ' ' + move;
    }
    send(position);
    std::string go;
    switch (analysis_model_) {
        case AnalysisModel::FixedSteps: go = "go depth " + std::to_string(analysis_value_); break;
        case AnalysisModel::FixedTime: go = "go movetime " + std::to_string(analysis_value_); break;
        case AnalysisModel::FixedNodes: go = "go nodes " + std::to_string(analysis_value_); break;
        case AnalysisModel::Infinite: go = "go infinite"; break;
    }
    if (!tactic_list.empty()) {
        go += " searchmoves";
        for (const auto& move : tactic_list) go += ' ' + move;
    }
    send(go);
}

void Engine::move_now() {
    send("stop");
}

void Engine::stop() {
    stop_flag_ = true;
    pondering_ = false;
    ponderhit_sent_ = false;
    send("stop");
}

void Engine::start_ponder(std::string_view fen, const std::vector<std::string>& moves,
                          std::string_view ponder_move) {
    stop();
    std::string position = "position fen " + std::string(fen);
    if (!moves.empty()) {
        position += " moves";
        for (const auto& move : moves) position += ' ' + move;
    }
    position += ' ' + std::string(ponder_move);
    send(position);
    send("go ponder" + ponder_limit());
    stop_flag_ = false;
    pondering_ = true;
    ponderhit_sent_ = false;
}

void Engine::ponderhit() {
    if (pondering_ && !ponderhit_sent_) {
        send("ponderhit");
        ponderhit_sent_ = true;
    }
}

std::string Engine::ponder_limit() const {
    switch (analysis_model_) {
        case AnalysisModel::FixedSteps: return " depth " + std::to_string(analysis_value_);
        case AnalysisModel::FixedTime: return " movetime " + std::to_string(analysis_value_);
        case AnalysisModel::FixedNodes: return " nodes " + std::to_string(analysis_value_);
        case AnalysisModel::Infinite: return {};
    }
    return {};
}

void Engine::set_thread_num(int value) {
    if (thread_num_ != value) { thread_num_ = value; thread_num_changed_ = true; }
}

void Engine::set_hash_size(int value) {
    if (hash_size_ != value) { hash_size_ = value; hash_size_changed_ = true; }
}

void Engine::set_analysis_model(AnalysisModel model, std::int64_t value) {
    analysis_model_ = model;
    analysis_value_ = value;
}

std::vector<std::string> Engine::sent_commands() const {
    std::lock_guard lock(command_mutex_);
    return commands_;
}

void Engine::close() {
    const bool had_process =
#ifdef _WIN32
        input_write_ != nullptr || output_read_ != nullptr || process_handle_ != nullptr;
#else
        input_write_ >= 0 || output_read_ >= 0 || process_id_ >= 0;
#endif
    if (had_process) {
        send("quit");
        running_ = false;
#ifdef _WIN32
        if (input_write_ != nullptr) { CloseHandle(static_cast<HANDLE>(input_write_)); input_write_ = nullptr; }
        if (output_read_ != nullptr) { CloseHandle(static_cast<HANDLE>(output_read_)); output_read_ = nullptr; }
        if (process_handle_ != nullptr) {
            WaitForSingleObject(static_cast<HANDLE>(process_handle_), 1000);
            if (WaitForSingleObject(static_cast<HANDLE>(process_handle_), 0) == WAIT_TIMEOUT) {
                TerminateProcess(static_cast<HANDLE>(process_handle_), 1);
            }
            CloseHandle(static_cast<HANDLE>(process_handle_));
            process_handle_ = nullptr;
        }
#else
        if (input_write_ >= 0) { ::close(input_write_); input_write_ = -1; }
        if (output_read_ >= 0) { ::close(output_read_); output_read_ = -1; }
        if (process_id_ >= 0) {
            int status = 0;
            if (waitpid(process_id_, &status, WNOHANG) == 0) { kill(process_id_, SIGTERM); waitpid(process_id_, &status, 0); }
            process_id_ = -1;
        }
#endif
    } else {
        running_ = false;
    }
    if (reader_thread_.joinable()) reader_thread_.join();
}

} // namespace xiangqi
