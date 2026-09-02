#include "xiangqi/openbook.hpp"

#include <iterator>
#include <random>

namespace xiangqi {

void OpenBook::sort(std::vector<BookData>& data, MoveRule rule) {
    switch (rule) {
        case MoveRule::BestScore:
            std::stable_sort(data.begin(), data.end(), [](const BookData& left, const BookData& right) {
                return left.score > right.score;
            });
            break;
        case MoveRule::BestWinRate:
            std::stable_sort(data.begin(), data.end(), [](const BookData& left, const BookData& right) {
                return left.win_rate > right.win_rate;
            });
            break;
        case MoveRule::FullRandom: {
            std::random_device device;
            std::mt19937 generator(device());
            std::shuffle(data.begin(), data.end(), generator);
            break;
        }
        case MoveRule::PositiveRandom: {
            std::vector<BookData> positive;
            std::vector<BookData> other;
            for (auto& item : data) {
                (item.score > 0 ? positive : other).push_back(std::move(item));
            }
            std::random_device device;
            std::mt19937 generator(device());
            if (positive.size() > 1) std::shuffle(positive.begin(), positive.end(), generator);
            std::stable_sort(other.begin(), other.end(), [](const BookData& left, const BookData& right) {
                return left.score > right.score;
            });
            data.clear();
            data.insert(data.end(), std::make_move_iterator(positive.begin()), std::make_move_iterator(positive.end()));
            data.insert(data.end(), std::make_move_iterator(other.begin()), std::make_move_iterator(other.end()));
            break;
        }
    }
}

std::vector<BookData> FunctionOpenBook::get(const Board& board, bool red_go) {
    return board_query_ ? board_query_(board, red_go) : std::vector<BookData>{};
}

std::vector<BookData> FunctionOpenBook::get(std::string_view fen, bool only_final_phase) {
    return fen_query_ ? fen_query_(fen, only_final_phase) : std::vector<BookData>{};
}

} // namespace xiangqi
