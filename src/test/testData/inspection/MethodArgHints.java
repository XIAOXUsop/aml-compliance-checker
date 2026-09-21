package sample;

import java.util.Map;

/**
 * 方法调用的实参、以及 Map 字面量的值——测试与 mock 数据最标准的两种写法。
 *
 * <p>它们此前**拿不到任何标识符提示**：`PsiMethodCallExpression` 不是 `PsiNamedElement`，
 * 而 identifierHint 只找具名祖先，于是下面这两处一条都不报。
 *
 * <p>两个值都**故意不通过校验位**（正确值分别是 …531 与 …1486），
 * 所以它们只能靠语义信号认出来——这正是这条用例要测的东西：
 * 值形态那一轮对它们是沉默的，报不报完全取决于方法名 / 键名有没有被取到。
 */
public class MethodArgHints {

    void setIdCard(String v) {
    }

    void consume(String v) {
    }

    void probe() {
        // 方法名 setIdCard 暗示身份证
        setIdCard("110101199003078532");

        // 键名 cardNo 暗示卡号
        Map.of("cardNo", "4539578763621480");

        // 对照：方法名与键名都不携带语义 → 不该报
        consume("110101199003078532");
        consume("4539578763621480");
    }
}
