#git clone https://github.com/luojilab/CSSParser.git
cmake_minimum_required(VERSION 3.8)

project(cssparser)

include_directories()

add_library(
        cssparser

        Vendor/gumbo/attribute.c
        Vendor/gumbo/attribute.h
        Vendor/gumbo/char_ref.c
        Vendor/gumbo/char_ref.h
        Vendor/gumbo/error.c
        Vendor/gumbo/error.h
        Vendor/gumbo/gumbo.h
        Vendor/gumbo/gumbo_edit.c
        Vendor/gumbo/gumbo_edit.h
        Vendor/gumbo/GumboInterface.cpp
        Vendor/gumbo/GumboInterface.h
        Vendor/gumbo/insertion_mode.h
        Vendor/gumbo/parser.c
        Vendor/gumbo/parser.h
        Vendor/gumbo/string_buffer.c
        Vendor/gumbo/string_buffer.h
        Vendor/gumbo/string_piece.c
        Vendor/gumbo/string_piece.h
        Vendor/gumbo/tag.c
        Vendor/gumbo/tag.in
        Vendor/gumbo/tag_enum.h
        Vendor/gumbo/tag_perf.h
        Vendor/gumbo/tag_sizes.h
        Vendor/gumbo/tag_strings.h
        Vendor/gumbo/token_type.h
        Vendor/gumbo/tokenizer.c
        Vendor/gumbo/tokenizer.h
        Vendor/gumbo/tokenizer_states.h
        Vendor/gumbo/UrlUtil.cpp
        Vendor/gumbo/UrlUtil.h
        Vendor/gumbo/utf8.c
        Vendor/gumbo/utf8.h
        Vendor/gumbo/util.c
        Vendor/gumbo/util.h
        Vendor/gumbo/vector.c
        Vendor/gumbo/vector.h


        Vendor/Utils/ContainerUtil.cpp
        Vendor/Utils/ContainerUtil.hpp
        Vendor/Utils/StringUtil.cpp
        Vendor/Utils/StringUtil.h

        Applicability/HTMLCSSRefAdaptor.cpp
        Applicability/HTMLCSSRefAdaptor.h
        Applicability/PseudoSelectorRefChiefJudge.cpp
        Applicability/PseudoSelectorRefChiefJudge.hpp

        CSSParser/Keyword/KeywordItem.cpp
        CSSParser/Keyword/KeywordItem.hpp

        CSSParser/Selectors/AttributeSelector.cpp
        CSSParser/Selectors/AttributeSelector.hpp
        CSSParser/Selectors/ClassSelector.cpp
        CSSParser/Selectors/ClassSelector.hpp
        CSSParser/Selectors/CombineSelector.cpp
        CSSParser/Selectors/CombineSelector.hpp
        CSSParser/Selectors/IdSelector.cpp
        CSSParser/Selectors/IdSelector.hpp
        CSSParser/Selectors/PseudoSelector.cpp
        CSSParser/Selectors/PseudoSelector.hpp
        CSSParser/Selectors/Selector.cpp
        CSSParser/Selectors/Selector.hpp
        CSSParser/Selectors/SelectorGroup.cpp
        CSSParser/Selectors/SelectorGroup.hpp
        CSSParser/Selectors/SelectorSequence.cpp
        CSSParser/Selectors/SelectorSequence.hpp
        CSSParser/Selectors/SelectorsHeader.h
        CSSParser/Selectors/SignSelector.cpp
        CSSParser/Selectors/SignSelector.hpp
        CSSParser/Selectors/TypeSelector.cpp
        CSSParser/Selectors/TypeSelector.hpp
        CSSParser/Selectors/UniversalSelector.cpp
        CSSParser/Selectors/UniversalSelector.hpp

        CSSParser/CSSLex.cpp
        CSSParser/CSSLex.hpp
        CSSParser/CSSLexStatus.h
        CSSParser/CSSParser.cpp
        CSSParser/CSSParser.hpp
        CSSParser/CSSParserStatus.h
)

