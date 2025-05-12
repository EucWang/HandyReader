# Copyright (c) 2022 Bartek Fabiszewski
# http://www.fabiszewski.net
#
# This file is part of libmobi.
# Licensed under LGPL, either version 3, or any later.
# See <http://www.gnu.org/licenses/>

set(mobi_SOURCES
		out_export.h
		miniz.c
		miniz.h
        buffer.c
        buffer.h
        compression.c
        compression.h
        config.h
        debug.c
        debug.h
        index.c
        index.h
        memory.c
        memory.h
        meta.c
        meta.h
        mobi.h
        parse_rawml.c
        parse_rawml.h
        read.c
        read.h
        structure.c
        structure.h
        util.c
        util.h
        write.c
        write.h
)

#if(USE_ENCRYPTION)
	list(APPEND mobi_SOURCES encryption.c
            encryption.h
            sha1.c
            sha1.h
            randombytes.c
            randombytes.h)
#endif(USE_ENCRYPTION)

#if(USE_XMLWRITER)
	list(APPEND mobi_SOURCES opf.c
            opf.h)
#	if(NOT USE_LIBXML2)
		list(APPEND mobi_SOURCES xmlwriter.c
                xmlwriter.h)
#	endif(NOT USE_LIBXML2)
#endif(USE_XMLWRITER)


add_library(mobi ${mobi_SOURCES})

set_target_properties(mobi PROPERTIES
                      OUTPUT_NAME "mobi"
                      SOVERSION ${PACKAGE_VERSION_MAJOR}
                      VERSION "${PACKAGE_VERSION}"
					  POSITION_INDEPENDENT_CODE ${BUILD_SHARED_LIBS}
					  C_VISIBILITY_PRESET hidden
					  VISIBILITY_INLINES_HIDDEN ON
					  MACOSX_RPATH 1)

#if(USE_MINIZ)
#	set(miniz_SOURCES
#            miniz.c
#            miniz.h
#	)
#	add_library(miniz OBJECT ${miniz_SOURCES})
#	target_compile_definitions(miniz PRIVATE
#							   MINIZ_NO_STDIO
#							   MINIZ_NO_ZLIB_COMPATIBLE_NAMES
#							   MINIZ_NO_TIME
#							   MINIZ_NO_ARCHIVE_APIS
#							   MINIZ_NO_ARCHIVE_WRITING_APIS
#							   _POSIX_C_SOURCE=200112L)
#	target_link_libraries(mobi PRIVATE miniz)
#endif(USE_MINIZ)

#if(USE_LIBXML2)
#	target_link_libraries(mobi PUBLIC LibXml2::LibXml2)
#endif(USE_LIBXML2)

#if(USE_ZLIB)
#	target_link_libraries(mobi PUBLIC ZLIB::ZLIB)
#endif(USE_ZLIB)
