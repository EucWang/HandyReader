#ifndef JSON_BUILDER_H
#define JSON_BUILDER_H

#include <cstdio>
#include <sstream>
#include <string>

namespace json_builder {

inline std::string escape(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (unsigned char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

inline std::string build_meta_info(
    const std::string& title, const std::string& author,
    const std::string& contributor, const std::string& subject,
    const std::string& publisher, const std::string& date,
    const std::string& description, const std::string& review,
    const std::string& imprint, const std::string& copyright,
    const std::string& isbn, const std::string& asin,
    const std::string& language, bool isEncrypted,
    const std::string& coverPath, uint32_t crc,
    const std::string& contentHash = "") {

    std::ostringstream j;
    j << "{"
      << "\"title\":\"" << escape(title) << "\","
      << "\"author\":\"" << escape(author) << "\","
      << "\"contributor\":\"" << escape(contributor) << "\","
      << "\"subject\":\"" << escape(subject) << "\","
      << "\"publisher\":\"" << escape(publisher) << "\","
      << "\"date\":\"" << escape(date) << "\","
      << "\"description\":\"" << escape(description) << "\","
      << "\"review\":\"" << escape(review) << "\","
      << "\"imprint\":\"" << escape(imprint) << "\","
      << "\"copyright\":\"" << escape(copyright) << "\","
      << "\"isbn\":\"" << escape(isbn) << "\","
      << "\"asin\":\"" << escape(asin) << "\","
      << "\"language\":\"" << escape(language) << "\","
      << "\"isEncrypted\":" << (isEncrypted ? "true" : "false") << ","
      << "\"coverPath\":\"" << escape(coverPath) << "\","
      << "\"crc\":" << static_cast<int32_t>(crc) << ","
      << "\"contentHash\":\"" << escape(contentHash) << "\""
      << "}";
    return j.str();
}

}

#endif
