//
// Created by wxn on 2025/6/19.
//

#ifndef UREAD_ZIP_EXT_H
#define UREAD_ZIP_EXT_H

#include <string>
#include "../util/log.h"

extern "C" {
#include "../unzip101e/unzip.h"
}

#include "string_ext.h"

class zip_ext {
public:

    /****
     * 得到全部的zip内文件路径名
     *
     */
    static std::vector<std::string> inner_zip_files(unzFile uf);

    static int read_zip_file(unzFile uf, const std::string &filename, std::string &zip_file_data);

/***
 * 将zip文件中的文件写入到目标文件中
 * @param uf
 * @param zipfilename
 * @param target_filename
 * @return
 */
    static int write_zip_item_to_file(unzFile uf, const std::string &zipfilename,
                                      const std::string &target_filename);
};


#endif //UREAD_ZIP_EXT_H
