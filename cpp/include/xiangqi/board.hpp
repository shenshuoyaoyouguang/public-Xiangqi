#pragma once

#include "xiangqi/model.hpp"

#include <array>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace xiangqi {

constexpr int BoardRows = 10;
constexpr int BoardCols = 9;
using Board = std::array<std::array<char, BoardCols>, BoardRows>;

class BoardRules {
public:
    static Board initial_board();
    static void init_board(Board& board);
    static bool can_go(const Board& board, int x1, int y1, int x2, int y2);
    static bool is_check(const Board& board, bool red);
    static bool is_mate(const Board& board, bool red);
    static bool validate(const Board& board);

    static std::string fen_code(const Board& board, std::optional<bool> red_go = std::nullopt);
    static Board from_fen(std::string_view fen);
    static void from_fen(Board& board, std::string_view fen);
    static bool is_reverse(std::string_view fen);

    static std::string step_for_engine(int x1, int y1, int x2, int y2);
    static std::optional<Step> step_for_board(std::string_view move);
    static std::string translate(const Board& board, std::string_view move, bool has_go = false);
    static std::optional<Step> translate_chinese(const Board& board, std::string_view move,
                                                 std::string* output = nullptr);
};

class ChessBoard {
public:
    explicit ChessBoard(std::string_view fen = {});

    const Board& board() const noexcept { return board_; }
    Board& mutable_board() noexcept { return board_; }
    void set_fen(std::string_view fen);
    std::string fen_code(bool red_go) const;

    std::optional<Step> step_for_board(std::string_view move) const;
    std::optional<std::string> move(std::string_view move);
    std::optional<std::string> move(int x1, int y1, int x2, int y2);
    std::vector<std::string> tactic_list(bool red_go) const;
    std::string translate(std::string_view move, bool has_go = false) const;
    std::string translate(const std::vector<std::string>& moves) const;

private:
    Board board_{};
};

} // namespace xiangqi
