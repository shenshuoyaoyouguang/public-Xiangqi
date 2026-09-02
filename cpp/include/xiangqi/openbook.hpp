#pragma once

#include "xiangqi/board.hpp"
#include "xiangqi/model.hpp"

#include <functional>
#include <string_view>
#include <utility>
#include <vector>

namespace xiangqi {

enum class MoveRule {
    BestScore,
    BestWinRate,
    PositiveRandom,
    FullRandom,
};

class OpenBook {
public:
    virtual ~OpenBook() = default;
    virtual std::vector<BookData> get(const Board& board, bool red_go) = 0;
    virtual std::vector<BookData> get(std::string_view fen, bool only_final_phase) = 0;
    virtual void close() {}

    std::vector<BookData> query(const Board& board, bool red_go, MoveRule rule) {
        auto result = get(board, red_go);
        sort(result, rule);
        return result;
    }
    std::vector<BookData> query(std::string_view fen, bool only_final_phase, MoveRule rule) {
        auto result = get(fen, only_final_phase);
        sort(result, rule);
        return result;
    }

protected:
    static void sort(std::vector<BookData>& data, MoveRule rule);
};

// 用于测试和上层接入自有数据库/云服务的无依赖适配器。
class FunctionOpenBook final : public OpenBook {
public:
    using BoardQuery = std::function<std::vector<BookData>(const Board&, bool)>;
    using FenQuery = std::function<std::vector<BookData>(std::string_view, bool)>;

    FunctionOpenBook(BoardQuery board_query, FenQuery fen_query)
        : board_query_(std::move(board_query)), fen_query_(std::move(fen_query)) {}
    std::vector<BookData> get(const Board& board, bool red_go) override;
    std::vector<BookData> get(std::string_view fen, bool only_final_phase) override;

private:
    BoardQuery board_query_;
    FenQuery fen_query_;
};

} // namespace xiangqi
