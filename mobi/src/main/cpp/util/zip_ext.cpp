//
// Created by wxn on 2025/6/19.
//

#include "zip_ext.h"


int zip_ext::read_zip_file(unzFile uf, const std::string &filename, std::string &zip_file_data) {
    int err = unzLocateFile(uf, filename.c_str(), 0);
    if (err != UNZ_OK) {
        LOGE("%s cannot find %s", __func__, filename.c_str());
        unzClose(uf);
        uf = nullptr;
        return 0;
    }
    err = unzOpenCurrentFile(uf);
    if (err != UNZ_OK) {
        LOGE("%s cannot open content.opf", __func__);
        unzClose(uf);
        uf = nullptr;
        return 0;
    }

    std::stringstream streambuffer;
    char bufferRead[8192] = {0};
    int read_bytes;
    //将数据写出到文件中
    while ((read_bytes = unzReadCurrentFile(uf, bufferRead, sizeof(bufferRead))) > 0) {
        streambuffer.write(bufferRead, read_bytes);
    }
    //关闭输出文件，
    unzCloseCurrentFile(uf);
    zip_file_data = streambuffer.str();
    return 1;
}

/***
 * 将zip文件中的文件写入到目标文件中
 * @param uf
 * @param zipfilename
 * @param target_filename
 * @return
 */
int zip_ext::write_zip_item_to_file(unzFile uf, const std::string &zipfilename,
                           const std::string &target_filename) {
    int err = unzLocateFile(uf, zipfilename.c_str(), 0);
    if (err != UNZ_OK) {
        LOGE("%s cannot find %s", __func__, zipfilename.c_str());
        unzClose(uf);
        uf = nullptr;
        return 0;
    }
    err = unzOpenCurrentFile(uf);
    if (err != UNZ_OK) {
        LOGE("%s cannot open content.opf", __func__);
        unzClose(uf);
        uf = nullptr;
        return 0;
    }

    FILE *file = fopen(target_filename.c_str(), "wb");
    if (file == nullptr)  {
        LOGE("%s cannot create file[%s]", __func__, target_filename.c_str());
        unzCloseCurrentFile(uf);
        return 0;
    }

    char bufferRead[8192] = {0};
    int read_bytes = 0;
    //将数据写出到文件中
    while ((read_bytes = unzReadCurrentFile(uf, bufferRead, sizeof(bufferRead))) > 0) {
        fwrite(bufferRead, 1, read_bytes, file);
    }
    fclose(file);

    //关闭输出文件，
    unzCloseCurrentFile(uf);
    return 1;
}