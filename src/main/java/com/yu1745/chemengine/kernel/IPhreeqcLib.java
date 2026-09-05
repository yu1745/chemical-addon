package com.yu1745.chemengine.kernel;

import com.sun.jna.Library;

/**
 * IPhreeqc C API 的 JNA 映射（iphreeqc/src/IPhreeqc.h，导出名未 mangle）。
 *
 * <p>只映射引擎门面需要的 ~12 个函数；全部为纯标量/字符串签名，无结构体无回调，
 * 因此不需要任何 C 胶水。IPQ_RESULT 枚举按 int 处理。
 */
public interface IPhreeqcLib extends Library {

    /** 创建一个新的 IPhreeqc 实例，返回实例 id（&lt;0 失败）。 */
    int CreateIPhreeqc();

    /** 销毁实例。IRN_RESULT：0=OK, 1=ERROR, 2=WARNING。 */
    int DestroyIPhreeqc(int id);

    /** 从字符串装载数据库。0=OK。 */
    int LoadDatabaseString(int id, String input);

    /** 执行 PHREEQC 输入脚本。返回本次运行遇到的错误数，0 才是成功。 */
    int RunString(int id, String input);

    int GetErrorStringOn(int id);

    int SetErrorStringOn(int id, int on);

    int GetErrorStringLineCount(int id);

    String GetErrorStringLine(int id, int n);

    int GetSelectedOutputStringOn(int id);

    int SetSelectedOutputStringOn(int id, int on);

    int GetSelectedOutputStringLineCount(int id);

    String GetSelectedOutputStringLine(int id, int n);

    int GetDumpStringOn(int id);

    /** DUMP 输出重定向到内存字符串（供存档）。须在运行含 DUMP 关键字的脚本前打开。 */
    int SetDumpStringOn(int id, int on);

    int GetDumpStringLineCount(int id);

    String GetDumpStringLine(int id, int n);
}
