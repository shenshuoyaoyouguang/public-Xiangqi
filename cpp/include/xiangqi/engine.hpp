#pragma once

#include "xiangqi/model.hpp"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

namespace xiangqi {

struct EngineCallbacks {
    std::function<void(std::string best_move, std::optional<std::string> ponder)> best_move;
    std::function<void(const ThinkData&)> think_detail;
    std::function<void(const std::vector<BookData>&)> show_book_results;
    std::function<void(std::string_view)> error;
};

class EngineProtocol {
public:
    explicit EngineProtocol(std::string protocol);

    std::string option_command(std::string_view name, std::string_view value) const;
    static bool validate_move(std::string_view move);
    static std::optional<std::pair<std::string, std::optional<std::string>>> parse_bestmove(std::string_view line);
    static ThinkData parse_info(std::string_view line);

private:
    std::string protocol_;
};

class Engine {
public:
    Engine(EngineConfig config, EngineCallbacks callbacks = {});
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    bool start();
    void close();
    bool running() const noexcept;

    void analysis(std::string_view fen, const std::vector<std::string>& moves,
                  std::vector<std::string> tactic_list = {});
    void move_now();
    void stop();
    void start_ponder(std::string_view fen, const std::vector<std::string>& moves,
                      std::string_view ponder_move);
    void ponderhit();
    bool is_pondering() const noexcept { return pondering_; }

    void set_thread_num(int value);
    void set_hash_size(int value);
    void set_analysis_model(AnalysisModel model, std::int64_t value);

    // 供单测和无进程集成测试驱动 stdout 状态机。
    void consume_line(std::string_view line);
    std::vector<std::string> sent_commands() const;

private:
    bool send(std::string command);
    void finish_send();
    bool wait_for_handshake(std::chrono::milliseconds timeout);
    void drain_writers();
    void close_locked();
    std::string ponder_limit() const;
    void handle_bestmove(std::string_view line);
    void handle_info(std::string_view line);
    void reader_loop();

    EngineConfig config_;
    EngineCallbacks callbacks_;
    EngineProtocol protocol_;
    std::atomic_bool running_{false};
    std::atomic_bool stop_flag_{false};
    std::atomic_bool pondering_{false};
    std::atomic_bool ponderhit_sent_{false};
    bool thread_num_changed_ = false;
    bool hash_size_changed_ = false;
    int thread_num_ = 0;
    int hash_size_ = 0;
    AnalysisModel analysis_model_ = AnalysisModel::Infinite;
    std::int64_t analysis_value_ = 0;

    mutable std::mutex lifecycle_mutex_;
    mutable std::mutex command_mutex_;
    mutable std::mutex process_mutex_;
    // 写协议见 send()/close_locked()：writers_active_ 登记在途写，
    // 排空后 close 才能关闭句柄；write_mutex_ 只串行化实际 I/O。
    mutable std::mutex write_mutex_;
    std::condition_variable write_cv_;
    int writers_active_ = 0;
    // 启动握手（uciok/ucciok）状态，handshake_mutex_ 保护。
    mutable std::mutex handshake_mutex_;
    std::condition_variable handshake_cv_;
    bool handshake_done_ = false;
    std::deque<std::string> commands_;
    std::thread reader_thread_;

#ifdef _WIN32
    void* process_handle_ = nullptr;
    void* input_write_ = nullptr;
    void* output_read_ = nullptr;
#else
    int process_id_ = -1;
    int input_write_ = -1;
    int output_read_ = -1;
#endif
};

} // namespace xiangqi
