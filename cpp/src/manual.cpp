#include "xiangqi/manual.hpp"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace xiangqi {
namespace {

std::string trim(std::string value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return {};
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

bool is_result(std::string_view token) {
    return token == "1-0" || token == "0-1" || token == "1/2-1/2" || token == "*";
}

bool is_iccs(std::string token) {
    if (token.size() == 5 && token[2] == '-') token.erase(2, 1);
    if (token.size() != 4) return false;
    return token[0] >= 'a' && token[0] <= 'i' && token[2] >= 'a' && token[2] <= 'i' &&
           token[1] >= '0' && token[1] <= '9' && token[3] >= '0' && token[3] <= '9';
}

struct MoveToken {
    std::string value;
    std::string comment;
};

std::vector<MoveToken> tokenize_moves(std::string_view text) {
    std::vector<MoveToken> tokens;
    std::string current;
    std::string comment;
    bool in_comment = false;
    int variation_depth = 0;
    for (char c : text) {
        if (in_comment) {
            if (c == '}') {
                in_comment = false;
                tokens.push_back({{}, trim(comment)});
                comment.clear();
            } else {
                comment += c;
            }
            continue;
        }
        if (c == '{') {
            if (!current.empty()) { tokens.push_back({std::move(current), {}}); current.clear(); }
            in_comment = true;
            continue;
        }
        if (c == '(') {
            if (!current.empty()) { tokens.push_back({std::move(current), {}}); current.clear(); }
            ++variation_depth;
            continue;
        }
        if (c == ')' && variation_depth > 0) {
            if (!current.empty()) { tokens.push_back({std::move(current), {}}); current.clear(); }
            --variation_depth;
            continue;
        }
        if (variation_depth > 0) continue;
        if (std::isspace(static_cast<unsigned char>(c))) {
            if (!current.empty()) { tokens.push_back({std::move(current), {}}); current.clear(); }
        } else {
            current += c;
        }
    }
    if (!current.empty()) tokens.push_back({std::move(current), {}});
    return tokens;
}

void translate_mainline(ChessManual& manual, bool chinese) {
    if (!manual.head) return;
    Board board = manual.fen_code.empty() ? BoardRules::initial_board() : BoardRules::from_fen(manual.fen_code);
    auto current = manual.head;
    while (current && !current->list.empty()) {
        if (current->next >= current->list.size()) current->next = 0;
        current = current->list[current->next];
        if (!current) break;
        if (chinese && !current->cn_move.empty()) {
            std::string normalized;
            const auto step = BoardRules::translate_chinese(board, current->cn_move, &normalized);
            current->move = normalized;
            if (step) {
                board[step->end.y][step->end.x] = board[step->start.y][step->start.x];
                board[step->start.y][step->start.x] = ' ';
            }
        } else if (!chinese && !current->move.empty()) {
            const auto step = BoardRules::step_for_board(current->move);
            if (step) {
                board[step->end.y][step->end.x] = board[step->start.y][step->start.x];
                board[step->start.y][step->start.x] = ' ';
            }
        }
    }
}

} // namespace

std::optional<ChessManual> PgnManual::from_text(std::string_view text) {
    ChessManual manual;
    manual.head = ManualRecord::score_record(0, "开始局面", 0);
    std::string format = "Chinese";
    std::size_t move_start = 0;

    std::size_t line_start = 0;
    while (line_start <= text.size()) {
        const auto line_end = text.find('\n', line_start);
        const auto line = trim(std::string(text.substr(line_start,
            line_end == std::string_view::npos ? line_end : line_end - line_start)));
        if (line.empty()) {
            if (line_end == std::string_view::npos) break;
            line_start = line_end + 1;
            move_start = line_start;
            continue;
        }
        if (line.front() != '[') break;
        const auto space = line.find(' ');
        const auto quote1 = line.find('"');
        const auto quote2 = line.rfind('"');
        if (space != std::string::npos && quote1 > space && quote2 > quote1) {
            const std::string tag = line.substr(1, space - 1);
            const std::string value = line.substr(quote1 + 1, quote2 - quote1 - 1);
            if (tag == "Event") manual.name = value;
            else if (tag == "Site") manual.city = value;
            else if (tag == "Date") manual.date = value;
            else if (tag == "Red") manual.red = value;
            else if (tag == "Black") manual.black = value;
            else if (tag == "FEN") manual.fen_code = value;
            else if (tag == "Format") format = value;
        }
        if (line_end == std::string_view::npos) {
            move_start = text.size();
            break;
        }
        line_start = line_end + 1;
        move_start = line_start;
    }

    auto current = manual.head;
    std::shared_ptr<ManualRecord> last;
    const auto tokens = tokenize_moves(text.substr(move_start));
    for (const auto& token_item : tokens) {
        if (!token_item.comment.empty()) {
            if (last) last->remark = token_item.comment;
            else manual.head->remark = token_item.comment;
            continue;
        }
        const auto& raw_token = token_item.value;
        if (is_result(raw_token)) break;
        if (raw_token.size() > 1 && raw_token.back() == '.') continue;
        if (!raw_token.empty() && std::isdigit(static_cast<unsigned char>(raw_token.front()))) {
            const auto dot = raw_token.find('.');
            if (dot != std::string::npos) continue;
        }
        std::string token = raw_token;
        const bool token_iccs = is_iccs(token);
        if (token_iccs || format == "ICCS") {
            for (char& c : token) {
                if (c == '-') c = 0;
                else c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
            }
            token.erase(std::remove(token.begin(), token.end(), '\0'), token.end());
            if (!is_iccs(token)) continue;
            auto record = std::make_shared<ManualRecord>(last ? last->id + 1 : 1, token, "");
            current->list.push_back(record);
            current->next = current->list.size() - 1;
            current = record;
            last = record;
        } else if (format == "Chinese") {
            auto record = std::make_shared<ManualRecord>(last ? last->id + 1 : 1, "", token);
            current->list.push_back(record);
            current->next = current->list.size() - 1;
            current = record;
            last = record;
        }
    }

    if (manual.fen_code.find(" r ") != std::string::npos) {
        manual.fen_code.replace(manual.fen_code.find(" r "), 3, " w ");
    }
    translate_mainline(manual, format == "Chinese");
    return manual;
}

std::optional<ChessManual> PgnManual::open(const std::filesystem::path& file) {
    std::ifstream input(file, std::ios::binary);
    if (!input) return std::nullopt;
    std::ostringstream buffer;
    buffer << input.rdbuf();
    std::string text = buffer.str();
    if (text.size() >= 3 && static_cast<unsigned char>(text[0]) == 0xEF &&
        static_cast<unsigned char>(text[1]) == 0xBB && static_cast<unsigned char>(text[2]) == 0xBF) {
        text.erase(0, 3);
    }
    return from_text(text);
}

bool PgnManual::save(const ChessManual& manual, const std::filesystem::path& file) {
    std::ofstream output(file, std::ios::binary);
    if (!output) return false;
    output << to_text(manual, true);
    return static_cast<bool>(output);
}

std::string PgnManual::to_text(const ChessManual& manual, bool include_remarks) {
    std::ostringstream output;
    output << "[Game \"Chinese Chess\"]\n"
           << "[Event \"" << manual.name << "\"]\n"
           << "[Site \"" << manual.city << "\"]\n"
           << "[Date \"" << manual.date << "\"]\n"
           << "[Red \"" << manual.red << "\"]\n"
           << "[Black \"" << manual.black << "\"]\n"
           << "[Result \"*\"]\n"
           << "[FEN \"" << manual.fen_code << "\"]\n"
           << "[Format \"Chinese\"]\n\n";
    if (!manual.head) return output.str() + "*";
    if (include_remarks && !manual.head->remark.empty()) output << '{' << manual.head->remark << "}\n";
    auto current = manual.head;
    while (current && !current->list.empty()) {
        if (current->next >= current->list.size()) break;
        current = current->list[current->next];
        if (!current) break;
        if (current->id % 2 == 1) output << (current->id + 1) / 2 << ". ";
        else output << "    ";
        output << (current->cn_move.empty() ? current->move : current->cn_move) << ' ';
        if (include_remarks && !current->remark.empty()) output << '{' << current->remark << '}';
        output << '\n';
    }
    output << '*';
    return output.str();
}

std::optional<ChessManual> ManualCodec::open(const std::filesystem::path& file, std::string_view format) {
    if (format == "pgn" || format == "PGN") return PgnManual::open(file);
    return std::nullopt;
}

bool ManualCodec::save(const ChessManual& manual, const std::filesystem::path& file, std::string_view format) {
    if (format == "pgn" || format == "PGN") return PgnManual::save(manual, file);
    return false;
}

} // namespace xiangqi
