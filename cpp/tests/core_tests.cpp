#include "xiangqi/board.hpp"
#include "xiangqi/engine.hpp"
#include "xiangqi/manual.hpp"
#include "xiangqi/openbook.hpp"

#include <algorithm>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <random>
#include <stdexcept>
#include <string_view>

using namespace xiangqi;

namespace {

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

void board_tests() {
    const Board initial = BoardRules::initial_board();
    require(initial[0][0] == 'r' && initial[0][4] == 'k' && initial[9][4] == 'K', "initial placement");
    require(BoardRules::fen_code(initial, true) ==
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1", "initial fen");
    require(BoardRules::step_for_engine(0, 0, 0, 1) == "a9a8", "engine coordinate");
    const auto parsed = BoardRules::step_for_board("h7e7");
    require(parsed && parsed->start.x == 7 && parsed->start.y == 2 && parsed->end.x == 4 && parsed->end.y == 2,
            "board coordinate");

    ChessBoard board;
    require(board.move(0, 0, 0, 6) == std::optional<std::string>("a9a3"), "rook move");
    require(board.board()[6][0] == 'r' && board.board()[0][0] == ' ', "rook board update");

    ChessBoard cannon;
    require(cannon.move(1, 7, 1, 2) == std::optional<std::string>("b2b7"), "cannon capture");

    ChessBoard facing("4k4/9/9/9/9/4r4/9/9/9/4K4 b");
    require(!facing.move(4, 5, 5, 5), "suicide move rejected");
    require(facing.board()[5][4] == 'r' && facing.board()[5][5] == ' ', "rejected move rollback");

    ChessBoard tactics;
    const auto moves = tactics.tactic_list(true);
    require(moves.size() == 44, "opening tactic count");
    require(std::find(moves.begin(), moves.end(), "h2e2") != moves.end(), "opening cannon move");
    require(std::find(moves.begin(), moves.end(), "h0g2") != moves.end(), "opening knight move");

    require(BoardRules::translate(tactics.board(), "h2e2") == "炮二平五", "translate red move");
    require(BoardRules::translate(tactics.board(), "h7e7") == "炮８平５", "translate black move");
    std::string normalized;
    require(BoardRules::translate_chinese(tactics.board(), "炮二平五", &normalized) && normalized == "h2e2",
            "translate chinese move");
    require(BoardRules::translate_chinese(tactics.board(), "马二进三", &normalized) && normalized == "h0g2",
            "translate chinese knight");
    Board same_file{};
    for (auto& row : same_file) row.fill(' ');
    same_file[0][4] = 'k';
    same_file[8][4] = 'R'; same_file[9][4] = 'R';
    require(BoardRules::translate(same_file, "e0e1") == "后车进一", "same-file notation");

    Board numbered_pawns{};
    for (auto& row : numbered_pawns) row.fill(' ');
    numbered_pawns[2][4] = numbered_pawns[4][4] = numbered_pawns[6][4] = numbered_pawns[8][4] = 'P';
    const std::string numbered_move = BoardRules::translate(numbered_pawns, "e3e4");
    require(numbered_move == "三兵进一", "numbered pawn notation");
    require(BoardRules::translate_chinese(numbered_pawns, numbered_move, &normalized) && normalized == "e3e4",
            "numbered pawn parsing");

    const Board invalid_fen = BoardRules::from_fen("9X/9/9/9/9/9/9/9/9/9");
    require(invalid_fen[0][0] == ' ' && invalid_fen[9][8] == ' ', "invalid fen symbol");
    const Board overflowing_fen = BoardRules::from_fen("8r2/9/9/9/9/9/9/9/9/9");
    require(overflowing_fen[0][0] == ' ' && overflowing_fen[0][8] == ' ', "overflowing fen rank");
    require(!BoardRules::translate_chinese(BoardRules::initial_board(), "兵一进九", &normalized),
            "out-of-bounds chinese move");

    require(BoardRules::is_reverse("7K1/9/9/9/9/9/9/9/9/1k7 w"), "reverse fen detection");
    const Board reversed = BoardRules::from_fen("7K1/9/9/9/9/9/9/9/9/1k7 w");
    require(reversed[0][7] == 'k' && reversed[9][1] == 'K', "reverse fen parsing");
    require(BoardRules::validate(initial), "validate initial board");

    Board check{};
    for (auto& row : check) row.fill(' ');
    check[9][4] = 'K'; check[0][4] = 'r'; check[0][3] = 'k';
    require(BoardRules::is_check(check, true), "rook check");
    check[5][4] = 'A';
    require(!BoardRules::is_check(check, true), "blocked rook check");
    check[5][4] = ' ';
    check[3][4] = 'c'; check[6][4] = 'A';
    require(BoardRules::is_check(check, true), "cannon check");
    check[7][4] = 'A';
    require(!BoardRules::is_check(check, true), "double screened cannon");

    Board mate{};
    for (auto& row : mate) row.fill(' ');
    mate[9][4] = 'K'; mate[7][3] = 'n'; mate[9][0] = 'r'; mate[8][0] = 'r'; mate[0][3] = 'k';
    require(BoardRules::is_mate(mate, true), "double rook mate");
    mate[8][0] = 'R';
    require(!BoardRules::is_mate(mate, true), "mate can be blocked");
}

void manual_tests() {
    const auto manual = PgnManual::from_text(
        "[Event \"测试\"]\n[Format \"Chinese\"]\n[FEN \"rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w\"]\n\n"
        "1. 炮二平五 马８进７ {说明} 2. 马二进三 *\n");
    require(manual && manual->head && manual->head->list.size() == 1, "pgn parse root");
    require(manual->head->list[0]->move == "h2e2", "pgn chinese conversion");
    require(manual->head->list[0]->list[0]->move == "h9g7", "pgn black conversion");
    require(manual->head->list[0]->list[0]->remark == "说明", "pgn remark");
    require(PgnManual::to_text(*manual).find("炮二平五") != std::string::npos, "pgn serialization");
}

std::string utf16_bytes(std::u16string_view text, bool little_endian) {
    std::string bytes;
    bytes.append(little_endian ? "\xff\xfe" : "\xfe\xff", 2);
    for (const char16_t character : text) {
        const auto value = static_cast<unsigned int>(character);
        if (little_endian) {
            bytes += static_cast<char>(value & 0xff);
            bytes += static_cast<char>((value >> 8) & 0xff);
        } else {
            bytes += static_cast<char>((value >> 8) & 0xff);
            bytes += static_cast<char>(value & 0xff);
        }
    }
    return bytes;
}

void write_binary(const std::filesystem::path& file, std::string_view bytes) {
    std::ofstream output(file, std::ios::binary);
    require(static_cast<bool>(output), "open encoding fixture");
    output.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
    require(static_cast<bool>(output), "write encoding fixture");
}

struct TestTempDir {
    std::filesystem::path path;
    ~TestTempDir() { std::filesystem::remove_all(path); }
};

// 唯一临时目录（RAII 清理），避免并发运行的测试进程互相覆盖/删除对方 fixture。
TestTempDir make_test_temp_dir() {
    std::random_device device;
    for (int attempt = 0; attempt < 32; ++attempt) {
        const std::filesystem::path candidate =
            std::filesystem::temp_directory_path() / ("xiangqi_core_tests_" + std::to_string(device()));
        if (std::filesystem::create_directory(candidate)) return TestTempDir{candidate};
    }
    throw std::runtime_error("create unique temp dir");
}

void manual_regression_tests() {
    const auto variations = PgnManual::from_text(
        "[Format \"Chinese\"]\n\n"
        "1. 炮二平五 (1... 马８进７ {变例注释}) 马８进７ {主线注释} *\n");
    require(variations && variations->head->list.size() == 1, "variation mainline");
    require(variations->head->list[0]->remark.empty(), "variation remark isolation");
    require(variations->head->list[0]->list[0]->remark == "主线注释", "mainline remark after variation");

    const auto empty_comment = PgnManual::from_text(
        "[Format \"Chinese\"]\n\n1. 炮二平五 {先前注释} {} *\n");
    require(empty_comment && empty_comment->head->list[0]->remark.empty(), "empty comment");

    const auto compact_move_numbers = PgnManual::from_text(
        "[Format \"Chinese\"]\n\n1.炮二平五 1...马８进７ *\n");
    require(compact_move_numbers && compact_move_numbers->head->list.size() == 1,
            "compact move number red move");
    require(compact_move_numbers->head->list[0]->move == "h2e2", "compact move number red conversion");
    require(compact_move_numbers->head->list[0]->list.size() == 1,
            "compact move number black move");
    require(compact_move_numbers->head->list[0]->list[0]->move == "h9g7", "compact move number black conversion");

    const auto escaped_tags = PgnManual::from_text(
        R"([Event "A \"quote\" and \\ path\nnext\rrow\tcolumn"]
[Format "Chinese"]

1. 炮二平五 *)");
    // 标准 PGN 只定义 \" 与 \\ 转义，\n 等未知转义按字面量保留。
    require(escaped_tags && escaped_tags->name == R"(A "quote" and \ path\nnext\rrow\tcolumn)", "tag unescape");
    const auto escaped_round_trip = PgnManual::from_text(PgnManual::to_text(*escaped_tags));
    require(escaped_round_trip && escaped_round_trip->name == escaped_tags->name, "tag escape round trip");

    ChessManual control_tag_manual;
    control_tag_manual.name = "a\nb";
    const auto control_tag_text = PgnManual::to_text(control_tag_manual);
    require(control_tag_text.find("[Event \"a b\"]") != std::string::npos, "control char in tag");
    ChessManual del_tag_manual;
    del_tag_manual.name = "a\x7f" "b";
    require(PgnManual::to_text(del_tag_manual).find("[Event \"a b\"]") != std::string::npos, "del char in tag");

    const auto iccs = PgnManual::from_text(
        "[Format \"ICCS\"]\n\n1. a0a1 b9b8 *\n");
    require(iccs && iccs->head->list.size() == 1 && iccs->head->list[0]->move == "a0a1",
            "iccs parse");
    const auto iccs_text = PgnManual::to_text(*iccs);
    require(iccs_text.find("[Format \"ICCS\"]") != std::string::npos, "iccs format output");
    const auto iccs_round_trip = PgnManual::from_text(iccs_text);
    require(iccs_round_trip && iccs_round_trip->head->list[0]->move == "a0a1" &&
            iccs_round_trip->head->list[0]->list[0]->move == "b9b8", "iccs output round trip");

    const auto uppercase_iccs = PgnManual::from_text("\nA0-A1 *\n");
    require(uppercase_iccs && uppercase_iccs->head->list[0]->move == "a0a1", "uppercase iccs parse");

    const TestTempDir temp_dir = make_test_temp_dir();
    const auto temp = temp_dir.path;
    for (const bool little_endian : {true, false}) {
        const auto file = temp / (little_endian ? "manual_utf16le.pgn" : "manual_utf16be.pgn");
        write_binary(file, utf16_bytes(std::u16string_view(u"[Event \"测试\"]\n[Format \"Chinese\"]\n\n1. 炮二平五 *\n"), little_endian));
        const auto decoded = PgnManual::open(file);
        const auto name = decoded ? decoded->name : std::string{};
        require(decoded && name == "测试", "utf16 input");
    }

    if (PgnManual::gbk_supported()) {
        const auto gbk_file = temp / "manual_gbk.pgn";
        const std::string gbk_name("\xb2\xe2\xca\xd4", 4);
        write_binary(gbk_file, "[Event \"" + gbk_name + "\"]\n[Format \"Chinese\"]\n\n1. \xb1\xb2 *\n");
        const auto gbk = PgnManual::open(gbk_file);
        const auto gbk_decoded_name = gbk ? gbk->name : std::string{};
        require(gbk && gbk_decoded_name == "测试", "gbk input");
    }

    // Latin-1 的 é（0xE9）不是合法 UTF-8/GBK 序列，必须走 ISO-8859-1 兜底而不是 nullopt。
    const auto latin1_file = temp / "manual_latin1.pgn";
    write_binary(latin1_file, "[Event \"Jos\xe9\"]\n[Format \"Chinese\"]\n\n1. \xb1\xb2 *\n");
    const auto latin1 = PgnManual::open(latin1_file);
    const auto latin1_name = latin1 ? latin1->name : std::string{};
    require(latin1.has_value(), "latin1 input opens");
#ifndef _WIN32
    // Windows 侧由 CP936 先行解码（历史行为），只验证文件可打开。
    require(latin1_name == "Jos\xc3\xa9", "latin1 decoded as iso-8859-1");
#endif
}

void openbook_tests() {
    auto book = FunctionOpenBook(
        [](const Board&, bool) {
            BookData first; first.move = "a0a1"; first.score = 1; first.win_rate = 20.0;
            BookData second; second.move = "b0b1"; second.score = 5; second.win_rate = 10.0;
            return std::vector<BookData>{first, second};
        },
        [](std::string_view, bool) {
            BookData item; item.move = "c0c1"; item.score = 2; item.win_rate = 90.0;
            return std::vector<BookData>{item};
        });
    auto results = book.query(BoardRules::initial_board(), true, MoveRule::BestScore);
    require(results.size() == 2 && results[0].move == "b0b1", "openbook score sort");
    auto fen_results = book.query("startpos", false, MoveRule::BestWinRate);
    require(fen_results.size() == 1 && fen_results[0].move == "c0c1", "openbook fen query");
}

void engine_tests() {
    EngineProtocol uci("uci");
    EngineProtocol ucci("ucci");
    require(uci.option_command("Threads", "2") == "setoption name Threads value 2", "uci option command");
    require(ucci.option_command("Threads", "2") == "setoption Threads 2", "ucci option command");
    const auto bestmove = EngineProtocol::parse_bestmove("bestmove h1e2 ponder h9g9");
    require(bestmove && bestmove->first == "h1e2" && bestmove->second == std::optional<std::string>("h9g9"),
            "bestmove parser");
    require(!EngineProtocol::parse_bestmove("bestmove invalid"), "invalid bestmove parser");
    const auto info = EngineProtocol::parse_info("info depth 12 score cp -34 time 1000 nps 200000 multipv 2 pv h2e2 h7e7");
    require(info.depth == 12 && info.score == -34 && info.time == 1000 && info.nps == 200000 && info.pv == 2 &&
            info.detail.size() == 2, "info parser");
    const auto malformed_info = EngineProtocol::parse_info(
        "info depth 999999999999999999999 nps -999999999999999999999 time 1");
    require(!malformed_info.depth && !malformed_info.nps && malformed_info.time == 1, "oversized info parser");

    EngineConfig config;
    config.protocol = "uci";
    config.options["Threads"] = "2";
    Engine engine(config);
    engine.set_analysis_model(AnalysisModel::FixedTime, 5000);
    engine.start_ponder("fen", {"h2e2"}, "h9g9");
    const auto commands = engine.sent_commands();
    require(commands.size() == 3 && commands[0] == "stop" && commands[1] == "position fen fen moves h2e2 h9g9" &&
            commands[2] == "go ponder movetime 5000", "ponder commands");
    require(engine.is_pondering(), "ponder state");
    engine.ponderhit();
    engine.ponderhit();
    require(engine.sent_commands().size() == 4 && engine.sent_commands().back() == "ponderhit", "ponderhit once");

    int callbacks = 0;
    std::string best;
    EngineCallbacks callbacks_config;
    callbacks_config.best_move = [&](std::string move, std::optional<std::string>) {
        ++callbacks;
        best = std::move(move);
    };
    Engine callback_engine(config, callbacks_config);
    callback_engine.start_ponder("fen", {}, "h9g9");
    callback_engine.ponderhit();
    callback_engine.consume_line("bestmove h1e2 ponder h9g9");
    require(callbacks == 1 && best == "h1e2" && !callback_engine.is_pondering(), "bestmove callback");

    Engine empty_moves_engine(config);
    empty_moves_engine.start_ponder("fen", {}, "h9g9");
    require(empty_moves_engine.sent_commands()[1] == "position fen fen moves h9g9", "empty ponder moves");

    Engine bounded_history_engine(config);
    for (int i = 0; i < 1100; ++i) bounded_history_engine.stop();
    require(bounded_history_engine.sent_commands().size() == 1024, "bounded command history");
}

} // namespace

int main() {
    try {
        board_tests();
        manual_tests();
        manual_regression_tests();
        openbook_tests();
        engine_tests();
        std::cout << "xiangqi_core_tests: OK\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "xiangqi_core_tests: FAIL: " << error.what() << '\n';
        return 1;
    }
}
