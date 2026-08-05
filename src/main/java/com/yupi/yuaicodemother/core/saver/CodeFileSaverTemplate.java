package com.yupi.yuaicodemother.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 抽象代码文件保存器- 模板方法模式
 *
 * @author yupi
 */
public abstract class CodeFileSaverTemplate<T> {

    // 文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output/";

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result
     * @return
     */
    public final File saveFile(T  result) {
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir();
        // 3. 保存文件（具体实现交给子类）
        saveFile(result, baseDirPath);
        // 4. 返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 保存单个文件
     * @param dirPath
     * @param filename
     * @param content
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + "/" +  filename;
            FileUtil.writeString(content, filePath, "utf-8");
        }
    }

    /**
     * 验证输入
     *
     * @param result
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码对象结果不能为空");
        }
    }

    /**
     * 构建文件的唯一路径（tmp/code_output/bizType_雪花 ID）
     * @return
     */
    protected String buildUniqueDir() {
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + "/" + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    protected abstract CodeGenTypeEnum getCodeType();

    protected abstract void saveFile(T result, String baseDirPath);
}
