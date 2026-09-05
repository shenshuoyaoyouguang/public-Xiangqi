#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace xiangqi {

struct Point {
    int x = 0;
    int y = 0;
};

struct Step {
    Point start;
    Point end;
};

struct MoveTip {
    Step first;
    Step second;
};

struct BookData {
    std::string move;
    std::string word;
    int score = 0;
    double win_rate = 0.0;
    int win_num = 0;
    int draw_num = 0;
    int lose_num = 0;
    std::string note;
    std::string source;
};

struct EngineConfig {
    std::string name;
    std::string path;
    std::string protocol;
    std::map<std::string, std::string> options;
};

struct ManualRecord {
    int id = 0;
    std::optional<int> score;
    std::string move;
    std::string cn_move;
    std::string remark;
    std::size_t next = 0;
    std::vector<std::shared_ptr<ManualRecord>> list;

    ManualRecord() = default;
    ManualRecord(int record_id, std::string record_move, std::string record_cn_move)
        : id(record_id), move(std::move(record_move)), cn_move(std::move(record_cn_move)) {}
    static std::shared_ptr<ManualRecord> score_record(int record_id, std::string name, int value) {
        auto record = std::make_shared<ManualRecord>();
        record->id = record_id;
        record->cn_move = std::move(name);
        record->score = value;
        return record;
    }
};

struct ChessManual {
    std::string name;
    std::string date;
    std::string city;
    std::string black;
    std::string red;
    std::string fen_code;
    std::shared_ptr<ManualRecord> head;
};

enum class AnalysisModel {
    FixedTime,
    FixedSteps,
    FixedNodes,
    Infinite,
};

struct ThinkData {
    std::optional<int> depth;
    std::optional<int> score;
    std::optional<int> mate;
    std::optional<int> pv;
    std::optional<std::int64_t> nps;
    std::optional<std::int64_t> time;
    std::vector<std::string> detail;
    std::string title;
    std::string body;
    bool valid = false;
};

} // namespace xiangqi
