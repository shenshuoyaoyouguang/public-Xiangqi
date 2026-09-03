#include "xiangqi/manual.hpp"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <limits>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#elif __has_include(<iconv.h>)
#define XIANGQI_HAS_ICONV 1
#include <cerrno>
#include <iconv.h>
#endif

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
    const char start_file = static_cast<char>(std::tolower(static_cast<unsigned char>(token[0])));
    const char end_file = static_cast<char>(std::tolower(static_cast<unsigned char>(token[2])));
    return start_file >= 'a' && start_file <= 'i' && end_file >= 'a' && end_file <= 'i' &&
           token[1] >= '0' && token[1] <= '9' && token[3] >= '0' && token[3] <= '9';
}

struct MoveToken {
    std::string value;
    std::string comment;
    bool is_comment = false;
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
                if (variation_depth == 0) tokens.push_back({{}, trim(comment), true});
                comment.clear();
            } else {
                comment += c;
            }
            continue;
        }
        if (c == '{') {
            if (variation_depth == 0 && !current.empty()) {
                tokens.push_back({std::move(current), {}});
                current.clear();
            }
            in_comment = true;
            continue;
        }
        if (c == '(') {
            if (variation_depth == 0 && !current.empty()) {
                tokens.push_back({std::move(current), {}});
                current.clear();
            }
            ++variation_depth;
            continue;
        }
        if (c == ')' && variation_depth > 0) {
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

std::string strip_move_number(std::string token) {
    std::size_t digits = 0;
    while (digits < token.size() && std::isdigit(static_cast<unsigned char>(token[digits]))) ++digits;
    if (digits == 0 || digits == token.size() || token[digits] != '.') return token;
    std::size_t prefix_end = digits;
    while (prefix_end < token.size() && token[prefix_end] == '.') ++prefix_end;
    token.erase(0, prefix_end);
    return token;
}

bool parse_tag_line(std::string_view line, std::string& tag, std::string& value) {
    if (line.size() < 4 || line.front() != '[') return false;
    std::size_t pos = 1;
    const auto is_space = [](char c) { return std::isspace(static_cast<unsigned char>(c)) != 0; };
    while (pos < line.size() && !is_space(line[pos])) ++pos;
    if (pos == 1 || pos == line.size()) return false;
    tag = std::string(line.substr(1, pos - 1));
    while (pos < line.size() && is_space(line[pos])) ++pos;
    if (pos == line.size() || line[pos] != '"') return false;
    ++pos;

    value.clear();
    bool closed = false;
    while (pos < line.size()) {
        const char c = line[pos++];
        if (c == '\\') {
            if (pos == line.size()) return false;
            const char escaped = line[pos++];
            switch (escaped) {
            case '"': value += '"'; break;
            case '\\': value += '\\'; break;
            default:
                // 标准 PGN 只定义 \" 与 \\；其余转义按字面量保留，保证与严格 PGN 读取器互操作。
                value += '\\';
                value += escaped;
                break;
            }
        } else if (c == '"') {
            closed = true;
            break;
        } else {
            value += c;
        }
    }
    if (!closed) return false;
    while (pos < line.size() && is_space(line[pos])) ++pos;
    if (pos == line.size() || line[pos++] != ']') return false;
    while (pos < line.size() && is_space(line[pos])) ++pos;
    return pos == line.size();
}

std::string escape_tag_value(std::string_view value) {
    // 标准 PGN 只允许 \" 与 \\ 两种转义；tag 行必须是单行文本，
    // 控制字符无法转义，显式替换为空格。
    std::string escaped;
    escaped.reserve(value.size());
    for (const char c : value) {
        if (static_cast<unsigned char>(c) < 0x20) escaped += ' ';
        else if (c == '"') escaped += "\\\"";
        else if (c == '\\') escaped += "\\\\";
        else escaped += c;
    }
    return escaped;
}

void append_utf8(std::uint32_t codepoint, std::string& output) {
    if (codepoint <= 0x7f) {
        output += static_cast<char>(codepoint);
    } else if (codepoint <= 0x7ff) {
        output += static_cast<char>(0xc0 | (codepoint >> 6));
        output += static_cast<char>(0x80 | (codepoint & 0x3f));
    } else if (codepoint <= 0xffff) {
        output += static_cast<char>(0xe0 | (codepoint >> 12));
        output += static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f));
        output += static_cast<char>(0x80 | (codepoint & 0x3f));
    } else {
        output += static_cast<char>(0xf0 | (codepoint >> 18));
        output += static_cast<char>(0x80 | ((codepoint >> 12) & 0x3f));
        output += static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f));
        output += static_cast<char>(0x80 | (codepoint & 0x3f));
    }
}

bool decode_utf16(std::string_view bytes, bool little_endian, std::string& output) {
    if (bytes.size() < 2 || (bytes.size() - 2) % 2 != 0) return false;
    output.clear();
    output.reserve((bytes.size() - 2) / 2);
    auto read_unit = [&](std::size_t offset) {
        const auto first = static_cast<unsigned char>(bytes[offset]);
        const auto second = static_cast<unsigned char>(bytes[offset + 1]);
        return static_cast<std::uint16_t>(little_endian ? first | (second << 8) : (first << 8) | second);
    };
    for (std::size_t offset = 2; offset < bytes.size(); offset += 2) {
        const std::uint16_t unit = read_unit(offset);
        std::uint32_t codepoint = unit;
        if (unit >= 0xd800 && unit <= 0xdbff) {
            if (offset + 3 >= bytes.size()) return false;
            const std::uint16_t low = read_unit(offset + 2);
            if (low < 0xdc00 || low > 0xdfff) return false;
            codepoint = 0x10000 + ((static_cast<std::uint32_t>(unit) - 0xd800) << 10) + low - 0xdc00;
            offset += 2;
        } else if (unit >= 0xdc00 && unit <= 0xdfff) {
            return false;
        }
        append_utf8(codepoint, output);
    }
    return true;
}

bool valid_utf8(std::string_view text) {
    for (std::size_t i = 0; i < text.size();) {
        const auto lead = static_cast<unsigned char>(text[i]);
        if (lead <= 0x7f) {
            ++i;
            continue;
        }
        std::size_t length = 0;
        if (lead >= 0xc2 && lead <= 0xdf) length = 2;
        else if (lead >= 0xe0 && lead <= 0xef) length = 3;
        else if (lead >= 0xf0 && lead <= 0xf4) length = 4;
        else return false;
        if (i + length > text.size()) return false;
        for (std::size_t j = 1; j < length; ++j) {
            if ((static_cast<unsigned char>(text[i + j]) & 0xc0) != 0x80) return false;
        }
        const auto second = static_cast<unsigned char>(text[i + 1]);
        if ((lead == 0xe0 && second < 0xa0) || (lead == 0xed && second > 0x9f) ||
            (lead == 0xf0 && second < 0x90) || (lead == 0xf4 && second > 0x8f)) return false;
        i += length;
    }
    return true;
}

#ifdef _WIN32
bool decode_windows_code_page(std::string_view bytes, unsigned int code_page, std::string& output) {
    if (bytes.size() > static_cast<std::size_t>((std::numeric_limits<int>::max)())) return false;
    const auto size = static_cast<int>(bytes.size());
    const DWORD flags = code_page == CP_UTF8 ? MB_ERR_INVALID_CHARS : 0;
    const int wide_size = MultiByteToWideChar(code_page, flags, bytes.data(), size, nullptr, 0);
    if (wide_size <= 0 && !bytes.empty()) return false;
    std::wstring wide(static_cast<std::size_t>(wide_size), L'\0');
    if (wide_size > 0 && MultiByteToWideChar(code_page, flags, bytes.data(), size,
                                              wide.data(), wide_size) != wide_size) return false;
    const int utf8_size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, wide.data(), wide_size,
                                              nullptr, 0, nullptr, nullptr);
    if (utf8_size <= 0 && !wide.empty()) return false;
    output.assign(static_cast<std::size_t>(utf8_size), '\0');
    if (utf8_size > 0 && WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, wide.data(), wide_size,
                                              output.data(), utf8_size, nullptr, nullptr) != utf8_size) return false;
    return true;
}
#endif

#ifdef XIANGQI_HAS_ICONV
bool decode_gbk_with_iconv(std::string_view bytes, std::string& output) {
    iconv_t converter = iconv_open("UTF-8", "GBK");
    if (converter == reinterpret_cast<iconv_t>(-1)) {
        converter = iconv_open("UTF-8", "CP936");
    }
    if (converter == reinterpret_cast<iconv_t>(-1)) return false;

    std::string input(bytes);
    char* input_data = input.data();
    std::size_t input_left = input.size();
    output.clear();
    while (input_left > 0) {
        char converted[4096];
        char* output_data = converted;
        std::size_t output_left = sizeof(converted);
        const auto result = iconv(converter, &input_data, &input_left, &output_data, &output_left);
        output.append(converted, static_cast<std::size_t>(output_data - converted));
        if (result == static_cast<std::size_t>(-1) && errno != E2BIG) {
            iconv_close(converter);
            output.clear();
            return false;
        }
    }
    iconv_close(converter);
    return true;
}
#endif

std::optional<std::string> decode_file_text(std::string_view bytes) {
    if (bytes.size() >= 3 && static_cast<unsigned char>(bytes[0]) == 0xef &&
        static_cast<unsigned char>(bytes[1]) == 0xbb && static_cast<unsigned char>(bytes[2]) == 0xbf) {
        bytes.remove_prefix(3);
        if (!valid_utf8(bytes)) return std::nullopt;
        return std::string(bytes);
    }
    if (bytes.size() >= 2 && static_cast<unsigned char>(bytes[0]) == 0xff &&
        static_cast<unsigned char>(bytes[1]) == 0xfe) {
        std::string decoded;
        if (!decode_utf16(bytes, true, decoded)) return std::nullopt;
        return decoded;
    }
    if (bytes.size() >= 2 && static_cast<unsigned char>(bytes[0]) == 0xfe &&
        static_cast<unsigned char>(bytes[1]) == 0xff) {
        std::string decoded;
        if (!decode_utf16(bytes, false, decoded)) return std::nullopt;
        return decoded;
    }
#ifdef _WIN32
    std::string decoded;
    if (decode_windows_code_page(bytes, CP_UTF8, decoded)) return decoded;
    if (decode_windows_code_page(bytes, 936, decoded)) return decoded;
#else
    if (valid_utf8(bytes)) return std::string(bytes);
#ifdef XIANGQI_HAS_ICONV
    std::string decoded;
    if (decode_gbk_with_iconv(bytes, decoded)) return decoded;
#endif
#endif
    // 兜底（对齐 Java 版最后一档 ISO-8859-1）：任意字节都可映射，逐字节转 UTF-8，
    // 保证 open 不因编码探测失败而返回 nullopt。
    std::string fallback;
    fallback.reserve(bytes.size() * 2);
    for (const char c : bytes) append_utf8(static_cast<unsigned char>(c), fallback);
    return fallback;
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
        std::string tag;
        std::string value;
        if (parse_tag_line(line, tag, value)) {
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
        if (token_item.is_comment) {
            if (last) last->remark = token_item.comment;
            else manual.head->remark = token_item.comment;
            continue;
        }
        const auto& raw_token = token_item.value;
        if (is_result(raw_token)) break;
        std::string token = strip_move_number(raw_token);
        if (token.empty()) continue;
        if (std::isdigit(static_cast<unsigned char>(token.front()))) continue;
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

bool PgnManual::gbk_supported() {
#ifdef _WIN32
    return true;
#elif defined(XIANGQI_HAS_ICONV)
    iconv_t converter = iconv_open("UTF-8", "GBK");
    if (converter != reinterpret_cast<iconv_t>(-1)) {
        iconv_close(converter);
        return true;
    }
    converter = iconv_open("UTF-8", "CP936");
    if (converter != reinterpret_cast<iconv_t>(-1)) {
        iconv_close(converter);
        return true;
    }
    return false;
#else
    return false;
#endif
}

std::optional<ChessManual> PgnManual::open(const std::filesystem::path& file) {
    std::ifstream input(file, std::ios::binary);
    if (!input) return std::nullopt;
    std::ostringstream buffer;
    buffer << input.rdbuf();
    const auto text = decode_file_text(buffer.str());
    if (!text) return std::nullopt;
    return from_text(*text);
}

bool PgnManual::save(const ChessManual& manual, const std::filesystem::path& file) {
    std::ofstream output(file, std::ios::binary);
    if (!output) return false;
    output << to_text(manual, true);
    return static_cast<bool>(output);
}

std::string PgnManual::to_text(const ChessManual& manual, bool include_remarks) {
    bool iccs = false;
    auto format_record = manual.head;
    while (format_record && !format_record->list.empty()) {
        if (format_record->next >= format_record->list.size()) break;
        format_record = format_record->list[format_record->next];
        if (!format_record) break;
        if (!format_record->move.empty() && format_record->cn_move.empty()) {
            iccs = true;
            break;
        }
        if (!format_record->cn_move.empty()) break;
    }

    std::ostringstream output;
    output << "[Game \"Chinese Chess\"]\n"
           << "[Event \"" << escape_tag_value(manual.name) << "\"]\n"
           << "[Site \"" << escape_tag_value(manual.city) << "\"]\n"
           << "[Date \"" << escape_tag_value(manual.date) << "\"]\n"
           << "[Red \"" << escape_tag_value(manual.red) << "\"]\n"
           << "[Black \"" << escape_tag_value(manual.black) << "\"]\n"
           << "[Result \"*\"]\n"
           << "[FEN \"" << escape_tag_value(manual.fen_code) << "\"]\n"
           << "[Format \"" << (iccs ? "ICCS" : "Chinese") << "\"]\n\n";
    if (!manual.head) return output.str() + "*";
    if (include_remarks && !manual.head->remark.empty()) output << '{' << manual.head->remark << "}\n";
    auto current = manual.head;
    while (current && !current->list.empty()) {
        if (current->next >= current->list.size()) break;
        current = current->list[current->next];
        if (!current) break;
        if (current->id % 2 == 1) output << (current->id + 1) / 2 << ". ";
        else output << "    ";
        output << (iccs ? current->move : (current->cn_move.empty() ? current->move : current->cn_move)) << ' ';
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
