package com.yu1745.chemengine.kernel;

/** PHREEQC 执行失败（RunString/LoadDatabaseString 返回非 0），携带原生错误文本。 */
public class IPhreeqcException extends RuntimeException {

    public IPhreeqcException(String message) {
        super(message);
    }
}
