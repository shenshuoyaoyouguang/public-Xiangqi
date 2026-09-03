#pragma once

#include "xiangqi/board.hpp"
#include "xiangqi/model.hpp"

#include <filesystem>
#include <string>

namespace xiangqi {

class PgnManual {
public:
    static std::optional<ChessManual> from_text(std::string_view text);
    static std::optional<ChessManual> open(const std::filesystem::path& file);
    static bool save(const ChessManual& manual, const std::filesystem::path& file);
    static std::string to_text(const ChessManual& manual, bool include_remarks = true);
    // 当前构建/系统是否可用 GBK(或 CP936) 解码器，供调用方按能力探测而非假设。
    static bool gbk_supported();
};

// TXQ 是 Java ObjectOutputStream 格式，不能由 C++ 标准库直接兼容。
// 这里提供明确的替代接口，避免把 Java 私有序列化格式误当作跨语言格式。
class ManualCodec {
public:
    static std::optional<ChessManual> open(const std::filesystem::path& file, std::string_view format);
    static bool save(const ChessManual& manual, const std::filesystem::path& file, std::string_view format);
};

} // namespace xiangqi
