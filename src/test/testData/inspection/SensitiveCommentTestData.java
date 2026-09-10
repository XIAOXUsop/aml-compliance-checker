// AML 合规检查器测试样例
// 正面样例 1：注释中含身份证
public class Sample1 {
    // 客户张三，身份证 110101199003078531，北京户口
    void sample() {}
}

// 正面样例 2：注释含手机号
class Sample2 {
    // 客服回访电话 13812345678
    void contact() {}
}

// 负面样例：普通注释无敏感数据
class Clean {
    // 该方法处理交易数据
    void ok() {}
}

// 负面样例：流水号不应误报（非身份证格式，无合法生日段）
class FalsePositiveCheck {
    // 订单流水号 88888888888888888888
    void order() {}
}
