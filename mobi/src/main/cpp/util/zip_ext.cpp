//
// Created by wxn on 2025/6/19.
//

#include "zip_ext.h"

std::vector<std::string> zip_ext::inner_zip_files(unzFile uf) {
    std::vector<std::string> ret;
    unz_global_info gi; //获取zip文件中的条目数
    int err = unzGetGlobalInfo(uf, &gi);
    if (err != UNZ_OK || gi.number_entry <= 0) {
        LOGE("%s cannot get zip file info", __func__);
        unzClose(uf);
        return ret;
    }
    int index = 0;
    err = unzGoToFirstFile(uf);
    if (err != UNZ_OK) {
        LOGE("%s goto first file failed", __func__ );
        return ret;
    }
    while(index < gi.number_entry) {
        err = unzOpenCurrentFile(uf);
        if (err != UNZ_OK) {
            LOGE("%s open current file failed", __func__ );
            return ret;
        }
        unz_file_info info;
        char file_name[256];

        err = unzGetCurrentFileInfo(uf, &info, file_name, 256, nullptr, 0, nullptr, 0);
        if (err != UNZ_OK) {
            LOGE("%s get current file failed", __func__ );
            unzCloseCurrentFile(uf);
            return ret;
        }
//        LOGD("%s:file_name[%s]", __func__, file_name);
        if (strlen(file_name) > 0) {
            ret.push_back(std::string(file_name));
        }

        unzCloseCurrentFile(uf);

        unzGoToNextFile(uf);

        index++;
    }

    return ret;
}


int zip_ext::read_zip_file(unzFile uf, const std::string &filename, std::string &zip_file_data) {
    int ret = 0;
    do {
        int err = unzLocateFile(uf, filename.c_str(), 0);
        if (err != UNZ_OK) {
            LOGE("%s cannot find %s", __func__, filename.c_str());
            ret = 0;
            break;
        }
        err = unzOpenCurrentFile(uf);
        if (err != UNZ_OK) {
            LOGE("%s cannot open content.opf", __func__);
            ret = 0;
            break;
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
        ret  = 1;
    } while(false);
    return ret;
}

/***
 * 将zip文件中的文件写入到目标文件中
 * @param uf
 * @param zipfilename
 * @param target_filename
 * @return
 */
int zip_ext::write_zip_item_to_file(unzFile uf,
                                    const std::string &zipfilename,
                                    const std::string &target_filename) {
    int err = unzLocateFile(uf, zipfilename.c_str(), 0);
    if (err != UNZ_OK) {
        LOGE("%s cannot find %s", __func__, zipfilename.c_str());
        return 0;
    }
    err = unzOpenCurrentFile(uf);
    if (err != UNZ_OK) {
        LOGE("%s cannot open content.opf", __func__);
        return 0;
    }

    FILE *file = fopen(target_filename.c_str(), "wb");
    if (file == nullptr) {
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