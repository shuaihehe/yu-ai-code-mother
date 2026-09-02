package com.yupi.yuaicodemother.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {

    /**
     * 下载项目为zip文件
     * @param projectPath 项目路径
     * @param downloadFileName 下载文件名
     * @param response HttpServletResponse，zip 内容直接写入其输出流
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);


}
