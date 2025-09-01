package com.wxn.base.bean


data class BookChapter(

    /***
     * 章节数据库id
     */
    var id : Long = 0,


    /***
     * 章节数据id
     */
    var chapterId : String = "",

    /***
     * 父章节id
     */
    var parentChapterId : String = "",

    /***
     * 书籍id，对应数据库中的ID
     */
    var bookId: Long,

    /***
     * 书籍索引，对应排序位置， 从0 开始
     */
    var chapterIndex: Int,

    /***
     * 章节名
     */
    var chapterName: String,

    /***
     * 章节创建时间
     */
    var createTimeValue: Long = 0,

    /***
     * 章节更新时间
     */
    var updateDate: String = "",

    /***
     * 章节更新时间戳
     */
    var updateTimeValue: Long = 0,

    /***
     * 章节对应的url地址
     */
    var chapterUrl: String? = "",

    /***
     * 章节对应的文件名
     */
    var srcName: String? = "",

    /***
     * 总章节数
     */
    var chaptersSize: Int = 0,

    var wordCount: Long = 0, //字数

    var picCount: Long = 0, //图片数

    var count : Long = 0, //字数+ 图片数

    var chapterProgress: Float = 0f
) {
}