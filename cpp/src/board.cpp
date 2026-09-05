#include "xiangqi/board.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <sstream>
#include <string>
#include <unordered_map>

namespace xiangqi {
namespace {

bool in_bounds(int x, int y) {
    return x >= 0 && x < BoardCols && y >= 0 && y < BoardRows;
}

bool is_red(char piece) {
    return piece >= 'A' && piece <= 'Z';
}

bool is_piece(char piece) {
    return std::string_view("krnbacpKRNBACP").find(piece) != std::string_view::npos;
}

bool line_clear(const Board& board, int x1, int y1, int x2, int y2) {
    if (x1 == x2) {
        const int low = std::min(y1, y2);
        const int high = std::max(y1, y2);
        for (int y = low + 1; y < high; ++y) {
            if (board[y][x1] != ' ') return false;
        }
    } else {
        const int low = std::min(x1, x2);
        const int high = std::max(x1, x2);
        for (int x = low + 1; x < high; ++x) {
            if (board[y1][x] != ' ') return false;
        }
    }
    return true;
}

int line_obstructions(const Board& board, int x1, int y1, int x2, int y2) {
    int count = 0;
    if (x1 == x2) {
        const int low = std::min(y1, y2);
        const int high = std::max(y1, y2);
        for (int y = low + 1; y < high; ++y) count += board[y][x1] != ' ';
    } else {
        const int low = std::min(x1, x2);
        const int high = std::max(x1, x2);
        for (int x = low + 1; x < high; ++x) count += board[y1][x] != ' ';
    }
    return count;
}

std::optional<Point> find_king(const Board& board, bool red) {
    const char king = red ? 'K' : 'k';
    for (int y = 0; y < BoardRows; ++y) {
        for (int x = 0; x < BoardCols; ++x) {
            if (board[y][x] == king) return Point{x, y};
        }
    }
    return std::nullopt;
}

std::string piece_name(char piece) {
    switch (piece) {
        case 'r': case 'R': return "车";
        case 'n': case 'N': return "马";
        case 'b': return "象";
        case 'B': return "相";
        case 'a': return "士";
        case 'A': return "仕";
        case 'k': return "将";
        case 'K': return "帅";
        case 'c': case 'C': return "炮";
        case 'p': return "卒";
        case 'P': return "兵";
        default: return "";
    }
}

const std::array<std::string, 10> chinese_digits = {
    "〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"
};

const std::array<std::string, 10> fullwidth_digits = {
    "０", "１", "２", "３", "４", "５", "６", "７", "８", "９"
};

std::vector<std::string> utf8_codepoints(std::string_view value) {
    std::vector<std::string> result;
    for (std::size_t i = 0; i < value.size();) {
        const unsigned char first = static_cast<unsigned char>(value[i]);
        std::size_t length = 1;
        if ((first & 0x80U) == 0) length = 1;
        else if ((first & 0xE0U) == 0xC0U) length = 2;
        else if ((first & 0xF0U) == 0xE0U) length = 3;
        else if ((first & 0xF8U) == 0xF0U) length = 4;
        if (i + length > value.size()) return {};
        result.emplace_back(value.substr(i, length));
        i += length;
    }
    return result;
}

int digit_value(std::string_view value, bool& red) {
    for (int i = 1; i <= 9; ++i) {
        if (value == chinese_digits[i]) {
            red = true;
            return i;
        }
        if (value == fullwidth_digits[i]) {
            red = false;
            return i;
        }
    }
    return 0;
}

std::string file_digit_text(int x, bool red) {
    const int index = red ? 9 - x : x + 1;
    return red ? chinese_digits[index] : fullwidth_digits[index];
}

std::string number_text(int number, bool red) {
    if (number < 1 || number > 9) return {};
    return red ? chinese_digits[number] : fullwidth_digits[number];
}

std::optional<std::string> same_file_prefix(const Board& board, const Point& from, const Point& to,
                                            char piece, bool red, bool has_go) {
    const auto is_other_piece = [&](int y, int x) {
        return board[y][x] == piece && !(has_go && y == to.y && x == to.x);
    };
    if (piece == 'r' || piece == 'R' || piece == 'c' || piece == 'C' || piece == 'n' || piece == 'N') {
        for (int y = 0; y < from.y; ++y) if (is_other_piece(y, from.x)) return red ? "后" : "前";
        for (int y = from.y + 1; y < BoardRows; ++y) if (is_other_piece(y, from.x)) return red ? "前" : "后";
        return std::nullopt;
    }
    if (piece != 'p' && piece != 'P') return std::nullopt;

    int before = 0;
    int after = 0;
    for (int y = 0; y < from.y; ++y) before += is_other_piece(y, from.x);
    for (int y = from.y + 1; y < BoardRows; ++y) after += is_other_piece(y, from.x);
    if (before == 0 && after == 0) return std::nullopt;
    if (before + after >= 3) return number_text((red ? before : after) + 1, red);

    int left = 0;
    int right = 0;
    for (int x = BoardCols - 1; x > from.x; --x) {
        int count = 0;
        for (int y = 0; y < BoardRows; ++y) count += is_other_piece(y, x);
        if (count > 1) { right += count; break; }
    }
    for (int x = from.x - 1; x >= 0; --x) {
        int count = 0;
        for (int y = 0; y < BoardRows; ++y) count += is_other_piece(y, x);
        if (count > 1) { left += count; break; }
    }
    if (left == 0 && right == 0) {
        if (before == 1 && after == 1) return "中";
        if (after == 0) return red ? "后" : "前";
        if (before == 0) return red ? "前" : "后";
    } else if (left > 0) {
        return number_text((red ? before : after + left) + 1, red);
    } else if (right > 0) {
        return number_text((red ? before + right : after) + 1, red);
    }
    return std::nullopt;
}

} // namespace

Board BoardRules::initial_board() {
    Board board{};
    init_board(board);
    return board;
}

void BoardRules::init_board(Board& board) {
    for (auto& row : board) row.fill(' ');
    const std::string black_back = "rnbakabnr";
    const std::string red_back = "RNBAKABNR";
    for (int x = 0; x < BoardCols; ++x) {
        board[0][x] = black_back[static_cast<std::size_t>(x)];
        board[9][x] = red_back[static_cast<std::size_t>(x)];
    }
    board[2][1] = board[2][7] = 'c';
    board[7][1] = board[7][7] = 'C';
    for (int x = 0; x < BoardCols; x += 2) {
        board[3][x] = 'p';
        board[6][x] = 'P';
    }
}

bool BoardRules::can_go(const Board& board, int x1, int y1, int x2, int y2) {
    if (!in_bounds(x1, y1) || !in_bounds(x2, y2) || (x1 == x2 && y1 == y2)) return false;
    const char piece = board[y1][x1];
    const char target = board[y2][x2];
    if (piece == ' ' || (target != ' ' && is_red(piece) == is_red(target))) return false;

    switch (piece) {
        case 'r': case 'R':
            return (x1 == x2 || y1 == y2) && line_clear(board, x1, y1, x2, y2);
        case 'n': case 'N': {
            const int dx = std::abs(x2 - x1), dy = std::abs(y2 - y1);
            if (!((dx == 1 && dy == 2) || (dx == 2 && dy == 1))) return false;
            const int leg_x = x1 + (dx == 2 ? (x2 > x1 ? 1 : -1) : 0);
            const int leg_y = y1 + (dy == 2 ? (y2 > y1 ? 1 : -1) : 0);
            return board[leg_y][leg_x] == ' ';
        }
        case 'b': case 'B': {
            const bool red = is_red(piece);
            if ((red && y2 < 5) || (!red && y2 > 4)) return false;
            return std::abs(x2 - x1) == 2 && std::abs(y2 - y1) == 2 &&
                   board[(y1 + y2) / 2][(x1 + x2) / 2] == ' ';
        }
        case 'a': case 'A': {
            const bool red = is_red(piece);
            return x2 >= 3 && x2 <= 5 && y2 >= (red ? 7 : 0) && y2 <= (red ? 9 : 2) &&
                   std::abs(x2 - x1) == 1 && std::abs(y2 - y1) == 1;
        }
        case 'k': case 'K': {
            const bool red = is_red(piece);
            return x2 >= 3 && x2 <= 5 && y2 >= (red ? 7 : 0) && y2 <= (red ? 9 : 2) &&
                   ((std::abs(x2 - x1) == 1 && y1 == y2) ||
                    (std::abs(y2 - y1) == 1 && x1 == x2));
        }
        case 'c': case 'C': {
            if (x1 != x2 && y1 != y2) return false;
            const int screens = line_obstructions(board, x1, y1, x2, y2);
            return (screens == 0 && target == ' ') || (screens == 1 && target != ' ');
        }
        case 'p':
            return (x2 == x1 && y2 == y1 + 1) ||
                   (y1 >= 5 && y2 == y1 && std::abs(x2 - x1) == 1);
        case 'P':
            return (x2 == x1 && y2 == y1 - 1) ||
                   (y1 <= 4 && y2 == y1 && std::abs(x2 - x1) == 1);
        default:
            return false;
    }
}

bool BoardRules::is_check(const Board& board, bool red) {
    const auto king = find_king(board, red);
    if (!king) return false;
    for (int y = 0; y < BoardRows; ++y) {
        for (int x = 0; x < BoardCols; ++x) {
            const char piece = board[y][x];
            if (piece != ' ' && is_red(piece) != red && can_go(board, x, y, king->x, king->y)) {
                return true;
            }
        }
    }
    // 将帅同列且无阻挡是独立于普通一步走法的攻击规则。
    const auto enemy_king = find_king(board, !red);
    if (enemy_king && enemy_king->x == king->x && line_clear(board, king->x, king->y,
                                                                enemy_king->x, enemy_king->y)) {
        return true;
    }
    return false;
}

bool BoardRules::is_mate(const Board& board, bool red) {
    if (!is_check(board, red)) return false;
    for (int y1 = 0; y1 < BoardRows; ++y1) {
        for (int x1 = 0; x1 < BoardCols; ++x1) {
            const char piece = board[y1][x1];
            if (piece == ' ' || is_red(piece) != red) continue;
            for (int y2 = 0; y2 < BoardRows; ++y2) {
                for (int x2 = 0; x2 < BoardCols; ++x2) {
                    if (board[y2][x2] == (red ? 'k' : 'K')) continue;
                    if (!can_go(board, x1, y1, x2, y2)) continue;
                    Board next = board;
                    next[y2][x2] = next[y1][x1];
                    next[y1][x1] = ' ';
                    if (!is_check(next, red)) return false;
                }
            }
        }
    }
    return true;
}

bool BoardRules::validate(const Board& board) {
    std::map<char, int> counts;
    for (int y = 0; y < BoardRows; ++y) {
        for (int x = 0; x < BoardCols; ++x) {
            const char piece = board[y][x];
            if (piece == ' ') continue;
            if (!is_piece(piece)) return false;
            if ((piece == 'k' || piece == 'K') && ((y > 2 && y < 7) || x < 3 || x > 5)) return false;
            if ((piece == 'b' || piece == 'B') &&
                ((y != 0 && y != 2 && y != 4 && y != 5 && y != 7 && y != 9) ||
                 (x != 0 && x != 2 && x != 4 && x != 6 && x != 8))) return false;
            if ((piece == 'a' || piece == 'A') &&
                ((y > 2 && y < 7) || x < 3 || x > 5 ||
                 (y <= 2 && (y + x) % 2 == 0) || (y >= 7 && (y + x) % 2 == 1))) return false;
            ++counts[piece];
        }
    }
    for (const auto& [piece, count] : counts) {
        const int max_count = (piece == 'p' || piece == 'P') ? 5 : 2;
        if (count > max_count) return false;
    }
    return counts['k'] == 1 && counts['K'] == 1;
}

std::string BoardRules::fen_code(const Board& board, std::optional<bool> red_go) {
    std::string result;
    for (int y = 0; y < BoardRows; ++y) {
        int empty = 0;
        for (int x = 0; x < BoardCols; ++x) {
            if (board[y][x] == ' ') {
                ++empty;
            } else {
                if (empty != 0) result += std::to_string(empty);
                empty = 0;
                result += board[y][x];
            }
        }
        if (empty != 0) result += std::to_string(empty);
        if (y != BoardRows - 1) result += '/';
    }
    if (red_go) result += *red_go ? " w - - 0 1" : " b - - 0 1";
    return result;
}

bool BoardRules::is_reverse(std::string_view fen) {
    const std::size_t red = fen.find('K');
    const std::size_t black = fen.find('k');
    if (red == std::string_view::npos && black == std::string_view::npos) return false;
    const std::size_t fifth_slash = [&] {
        std::size_t pos = 0;
        for (int i = 0; i < 5; ++i) {
            pos = fen.find('/', pos + 1);
            if (pos == std::string_view::npos) return std::string_view::npos;
        }
        return pos;
    }();
    if (red == std::string_view::npos || black == std::string_view::npos) {
        return red == std::string_view::npos ? black > fifth_slash : red < fifth_slash;
    }
    return red < black;
}

void BoardRules::from_fen(Board& board, std::string_view fen) {
    for (auto& row : board) row.fill(' ');
    Board parsed{};
    for (auto& row : parsed) row.fill(' ');
    try {
        const std::size_t space = fen.find(' ');
        std::string placement(fen.substr(0, space));
        std::vector<std::string> rows;
        std::size_t begin = 0;
        while (begin <= placement.size()) {
            const std::size_t end = placement.find('/', begin);
            rows.push_back(placement.substr(begin, end == std::string::npos ? end : end - begin));
            if (end == std::string::npos) break;
            begin = end + 1;
        }
        if (rows.size() != BoardRows) return;
        if (is_reverse(fen)) {
            for (int y = 0; y < BoardRows / 2; ++y) {
                std::reverse(rows[y].begin(), rows[y].end());
                std::reverse(rows[BoardRows - 1 - y].begin(), rows[BoardRows - 1 - y].end());
                std::swap(rows[y], rows[BoardRows - 1 - y]);
            }
        }
        for (int y = 0; y < BoardRows; ++y) {
            int x = 0;
            for (char piece : rows[y]) {
                if (piece >= '1' && piece <= '9') {
                    const int empty = piece - '0';
                    if (x > BoardCols - empty) return;
                    x += empty;
                } else if (is_piece(piece)) {
                    if (x >= BoardCols) return;
                    parsed[y][x++] = piece;
                } else {
                    return;
                }
            }
            if (x != BoardCols) return;
        }
        board = parsed;
    } catch (...) {
        for (auto& row : board) row.fill(' ');
    }
}

Board BoardRules::from_fen(std::string_view fen) {
    Board board{};
    from_fen(board, fen);
    return board;
}

std::string BoardRules::step_for_engine(int x1, int y1, int x2, int y2) {
    if (!in_bounds(x1, y1) || !in_bounds(x2, y2)) return {};
    std::string result;
    result += static_cast<char>('a' + x1);
    result += static_cast<char>('0' + (9 - y1));
    result += static_cast<char>('a' + x2);
    result += static_cast<char>('0' + (9 - y2));
    return result;
}

std::optional<Step> BoardRules::step_for_board(std::string_view move) {
    if (move.size() != 4 || move[0] < 'a' || move[0] > 'i' || move[2] < 'a' || move[2] > 'i' ||
        move[1] < '0' || move[1] > '9' || move[3] < '0' || move[3] > '9') return std::nullopt;
    return Step{Point{move[0] - 'a', 9 - (move[1] - '0')},
                Point{move[2] - 'a', 9 - (move[3] - '0')}};
}

std::string BoardRules::translate(const Board& board, std::string_view move, bool has_go) {
    const auto step = step_for_board(move);
    if (!step) return std::string(move);
    const Point from = step->start;
    const Point to = step->end;
    const char piece = has_go ? board[to.y][to.x] : board[from.y][from.x];
    const std::string name = piece_name(piece);
    if (name.empty()) return "null";
    const bool red = is_red(piece);
    std::string result;
    if (const auto prefix = same_file_prefix(board, from, to, piece, red, has_go)) result = *prefix + name;
    else result = name + file_digit_text(from.x, red);
    if (from.y == to.y && from.x != to.x) {
        result += "平";
        result += file_digit_text(to.x, red);
    } else if (from.x == to.x) {
        result += red ? (from.y > to.y ? "进" : "退") : (from.y < to.y ? "进" : "退");
        result += red ? chinese_digits[std::abs(from.y - to.y)] : fullwidth_digits[std::abs(from.y - to.y)];
    } else {
        result += red ? (from.y > to.y ? "进" : "退") : (from.y < to.y ? "进" : "退");
        result += file_digit_text(to.x, red);
    }
    return result;
}

std::optional<Step> BoardRules::translate_chinese(const Board& board, std::string_view move,
                                                   std::string* output) {
    const auto chars = utf8_codepoints(move);
    if (chars.size() < 4) {
        if (output) *output = std::string(move);
        return std::nullopt;
    }
    const std::string& first = chars[0];
    const std::string& second = chars[1];
    const std::string& action = chars[2];
    const std::string& destination = chars[3];
    bool red = false;
    const int destination_file = digit_value(destination, red);
    if (destination_file == 0) {
        if (output) *output = std::string(move);
        return std::nullopt;
    }
    char piece = 0;
    const std::unordered_map<std::string, char> piece_map = {
        {"车", 'r'}, {"马", 'n'}, {"象", 'b'}, {"相", 'B'}, {"士", 'a'}, {"仕", 'A'},
        {"将", 'k'}, {"帅", 'K'}, {"炮", 'c'}, {"卒", 'p'}, {"兵", 'P'}
    };
    bool prefix_red = false;
    const int prefix_value = digit_value(first, prefix_red);
    const bool numeric_prefix = prefix_value != 0;
    const bool qualifier = first == "前" || first == "中" || first == "后";
    const auto piece_it = piece_map.find(qualifier || numeric_prefix ? second : first);
    if (piece_it == piece_map.end()) {
        if (output) *output = std::string(move);
        return std::nullopt;
    }
    piece = piece_it->second;
    if (red) piece = static_cast<char>(std::toupper(static_cast<unsigned char>(piece)));
    else piece = static_cast<char>(std::tolower(static_cast<unsigned char>(piece)));

    int from_x = -1;
    int from_y = -1;
    if (numeric_prefix) {
        if (prefix_red != red || prefix_value > 5 || (piece != 'p' && piece != 'P')) {
            if (output) *output = std::string(move);
            return std::nullopt;
        }
        std::array<std::vector<int>, BoardCols> pawn_files;
        for (int x = 0; x < BoardCols; ++x) {
            for (int y = 0; y < BoardRows; ++y) {
                if (board[y][x] == piece) pawn_files[x].push_back(y);
            }
            if (pawn_files[x].size() == 1) pawn_files[x].clear();
        }
        int number = prefix_value;
        if (red) {
            for (int x = BoardCols - 1; x >= 0 && from_y < 0; --x) {
                if (number > static_cast<int>(pawn_files[x].size())) {
                    number -= static_cast<int>(pawn_files[x].size());
                } else if (!pawn_files[x].empty()) {
                    from_x = x;
                    from_y = pawn_files[x][number - 1];
                }
            }
        } else {
            for (int x = 0; x < BoardCols && from_y < 0; ++x) {
                if (number > static_cast<int>(pawn_files[x].size())) {
                    number -= static_cast<int>(pawn_files[x].size());
                } else if (!pawn_files[x].empty()) {
                    from_x = x;
                    from_y = pawn_files[x][pawn_files[x].size() - number];
                }
            }
        }
    } else if (!qualifier) {
        bool origin_red = red;
        const int origin_file = digit_value(second, origin_red);
        if (origin_file == 0 || origin_red != red) {
            if (output) *output = std::string(move);
            return std::nullopt;
        }
        from_x = red ? 9 - origin_file : origin_file - 1;
        for (int y = 0; y < BoardRows; ++y) {
            if (board[y][from_x] == piece) {
                from_y = y;
                break;
            }
        }
    } else {
        for (int x = 0; x < BoardCols && from_y < 0; ++x) {
            std::vector<int> rows;
            for (int y = 0; y < BoardRows; ++y) if (board[y][x] == piece) rows.push_back(y);
            if ((piece == 'p' || piece == 'P') ? (rows.size() == 2 || rows.size() == 3) : rows.size() == 2) {
                from_x = x;
                if (first == "前") from_y = red ? rows.front() : rows.back();
                else if (first == "中" && rows.size() >= 3) from_y = rows[1];
                else if (first == "后") from_y = red ? rows.back() : rows.front();
            }
        }
    }
    if (!in_bounds(from_x, from_y)) {
        if (output) *output = std::string(move);
        return std::nullopt;
    }

    if (action != "平" && action != "进" && action != "退") {
        if (output) *output = std::string(move);
        return std::nullopt;
    }

    int to_x = from_x;
    int to_y = from_y;
    const int distance = destination_file;
    if (action == "平") {
        to_x = red ? 9 - distance : distance - 1;
    } else if (action == "进" || action == "退") {
        const bool forward = action == "进";
        if (piece == 'r' || piece == 'c' || piece == 'p' || piece == 'k' ||
            piece == 'R' || piece == 'C' || piece == 'P' || piece == 'K') {
            to_y = red ? from_y - (forward ? distance : -distance)
                       : from_y + (forward ? distance : -distance);
        } else if (piece == 'n' || piece == 'N') {
            to_x = red ? 9 - distance : distance - 1;
            const int delta = std::abs(from_x - to_x) == 1 ? 2 : 1;
            to_y = red ? from_y - (forward ? delta : -delta)
                       : from_y + (forward ? delta : -delta);
        } else if (piece == 'b' || piece == 'B') {
            to_x = red ? 9 - distance : distance - 1;
            const int delta = forward ? 2 : -2;
            to_y = red ? from_y - delta : from_y + delta;
        } else if (piece == 'a' || piece == 'A') {
            to_x = red ? 9 - distance : distance - 1;
            const int delta = forward ? 1 : -1;
            to_y = red ? from_y - delta : from_y + delta;
        }
    }
    if (!in_bounds(to_x, to_y)) {
        if (output) *output = std::string(move);
        return std::nullopt;
    }
    const Step result{Point{from_x, from_y}, Point{to_x, to_y}};
    if (output) *output = step_for_engine(from_x, from_y, to_x, to_y);
    return result;
}

ChessBoard::ChessBoard(std::string_view fen) : board_(fen.empty() ? BoardRules::initial_board() : BoardRules::from_fen(fen)) {}

void ChessBoard::set_fen(std::string_view fen) {
    board_ = fen.empty() ? BoardRules::initial_board() : BoardRules::from_fen(fen);
}

std::string ChessBoard::fen_code(bool red_go) const {
    return BoardRules::fen_code(board_, red_go);
}

std::optional<Step> ChessBoard::step_for_board(std::string_view move) const {
    return BoardRules::step_for_board(move);
}

std::optional<std::string> ChessBoard::move(std::string_view move_text) {
    const auto step = BoardRules::step_for_board(move_text);
    if (!step) return std::nullopt;
    return move(step->start.x, step->start.y, step->end.x, step->end.y);
}

std::optional<std::string> ChessBoard::move(int x1, int y1, int x2, int y2) {
    if (!in_bounds(x1, y1) || !in_bounds(x2, y2) || board_[y1][x1] == ' ') return std::nullopt;
    const char captured = board_[y2][x2];
    board_[y2][x2] = board_[y1][x1];
    board_[y1][x1] = ' ';
    const bool red = is_red(board_[y2][x2]);
    if (BoardRules::is_check(board_, red)) {
        board_[y1][x1] = board_[y2][x2];
        board_[y2][x2] = captured;
        return std::nullopt;
    }
    return BoardRules::step_for_engine(x1, y1, x2, y2);
}

std::vector<std::string> ChessBoard::tactic_list(bool red_go) const {
    std::vector<std::string> result;
    for (int y1 = 0; y1 < BoardRows; ++y1) {
        for (int x1 = 0; x1 < BoardCols; ++x1) {
            if (board_[y1][x1] == ' ' || is_red(board_[y1][x1]) != red_go) continue;
            for (int y2 = 0; y2 < BoardRows; ++y2) {
                for (int x2 = 0; x2 < BoardCols; ++x2) {
                    if (!BoardRules::can_go(board_, x1, y1, x2, y2)) continue;
                    Board next = board_;
                    if (next[y2][x2] == (red_go ? 'k' : 'K')) continue;
                    next[y2][x2] = next[y1][x1];
                    next[y1][x1] = ' ';
                    if (!BoardRules::is_check(next, red_go)) {
                        result.push_back(BoardRules::step_for_engine(x1, y1, x2, y2));
                    }
                }
            }
        }
    }
    return result;
}

std::string ChessBoard::translate(std::string_view move_text, bool has_go) const {
    return BoardRules::translate(board_, move_text, has_go);
}

std::string ChessBoard::translate(const std::vector<std::string>& moves) const {
    Board copy = board_;
    std::string result;
    for (std::size_t i = 0; i < moves.size(); ++i) {
        if (i != 0) result += "  ";
        result += BoardRules::translate(copy, moves[i], false);
        const auto step = BoardRules::step_for_board(moves[i]);
        if (step) {
            copy[step->end.y][step->end.x] = copy[step->start.y][step->start.x];
            copy[step->start.y][step->start.x] = ' ';
        }
    }
    return result;
}

} // namespace xiangqi
