//
// Created by MAC on 2025/6/18.
//

#include "epub_util.h"

const std::string container = "META-INF/container.xml";
const std::string mimetype = "mimetype";


int epub_util::epub_init() {

    return 1;
}

int epub_util::load_epub(std::string fullpath,  //文件路径
                     std::string &coverPath,    //封面路径

                     std::string &title,
                     std::string &author,
                     std::string &contributor,

                     std::string &subject,
                     std::string &publisher,
                     std::string &date,

                     std::string &description,
                     std::string &review,
                     std::string &imprint,

                     std::string &copyright,
                     std::string &isbn,
                     std::string &asin,

                     std::string &language,
                     std::string &identifier,
                     bool &isEncrypted) {

    unzFile uf = unzOpen(fullpath.c_str());
    if (uf == nullptr) {
        LOGE("%s cannot open file[%s]", __func__, fullpath.c_str());
        return 0;
    }

    unz_global_info gi; //获取zip文件中的条目数
    int err = unzGetGlobalInfo(uf, &gi);
    if (err != UNZ_OK) {
        LOGE("%s cannot get zip file info", __func__);
        unzClose(uf);
        uf = nullptr;
        return 0;
    }
    
    err = unzLocateFile(uf, "OEBPS/content.opf", 0);
    if (err != UNZ_OK) {
        LOGE("%s cannot find content.opf", __func__);
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
    char bufferRead[8192] = { 0 };
    int read_bytes;
    //将数据写出到文件中
    while((read_bytes = unzReadCurrentFile(uf, bufferRead, sizeof(bufferRead))) > 0) {
        streambuffer.write(bufferRead, read_bytes);
    }
    //关闭输出文件，
    unzCloseCurrentFile(uf);
    std::string info = streambuffer.str();
    
//    for (int i = 0; i < gi.number_entry; ++i) {
//        unz_file_info file_info;
//        char filename_inzip[256] = { 0 };
//        //OEBPS/content.opf
//        //OEBPS/toc.ncx
//
//        //获取文件信息
//        err = unzGetCurrentFileInfo(uf, &file_info, filename_inzip, sizeof(filename_inzip), nullptr, 0, nullptr, 0);
//        if (err != UNZ_OK) {
//            LOGE("%s can't read file info[%d]", __func__, i);
//            unzClose(uf);
//            uf = nullptr;
//            return 0;
//        }
////        使用 unzLocateFile 函数定位到目标文件
////        int result = unzLocateFile(unz, "data.txt", 0);
////        if (result != UNZ_OK) {
////            // 处理错误
////        }
//
//        //打开文件
//        err = unzOpenCurrentFile(uf);
//        if (err != UNZ_OK) {
//            LOGE("%s cannt open file[%s]", __func__, filename_inzip);
//            unzClose(uf);
//            uf = nullptr;
//            return 0;
//        }
//        //创建输出文件
////        std::string outpath = app_ext::appCacheDir + "/" + std::string(filename_inzip, strlen(filename_inzip));
////        FILE *out = fopen(outpath.c_str(), "wb");
////        if (out == nullptr) {
////        }
//        std::stringstream streambuffer;
//        char bufferRead[8192] = { 0 };
//        int read_bytes;
//        //将数据写出到文件中
//        while((read_bytes = unzReadCurrentFile(uf, bufferRead, sizeof(bufferRead))) > 0) {
////            fwrite(buffer, 1, read_bytes, out);
////            fflush(out);
//            streambuffer.write(bufferRead, read_bytes);
//        }
//        //关闭输出文件，
////        fclose(out);
//        unzCloseCurrentFile(uf);
//
//        //移动到下一个条目
//        if ((i + 1) < gi.number_entry) {
//            err = unzGoToNextFile(uf);
//            if (err != UNZ_OK) {
//                LOGE("%s cannot move to next file", __func__);
//                unzClose(uf);
//                return 0;
//            }
//        }
//    }

    unzClose(uf);

    return 1;
}

int epub_util::getChapters(/*out*/std::vector<NavPoint> &points) {
    return 1;
}

int epub_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) {
    return 1;
}

int epub_util::getCss(std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos) {
    return 1;
}

int32_t epub_util::getWordCount(std::vector<std::pair<int32_t, int32_t>> &wordCounts) {

    return 1;
}
