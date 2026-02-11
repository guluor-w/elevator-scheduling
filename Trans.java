// 工具类通常声明为final且包含私有构造器，防止实例化
public final class Trans {
    private Trans() {} // 防止实例化

    public static String trans1(int floor) {
        switch (floor) {
            case -1: return "B1";
            case -2: return "B2";
            case -3: return "B3";
            case -4: return "B4";
            case 0: return "F1";
            case 1: return "F2";
            case 2: return "F3";
            case 3: return "F4";
            case 4: return "F5";
            case 5: return "F6";
            default: return "F7";
        }
    }

    public static int trans2(String floor) {
        switch (floor) {
            case "B1": return -1;
            case "B2": return -2;
            case "B3": return -3;
            case "B4": return -4;
            case "F1": return 0;
            case "F2": return 1;
            case "F3": return 2;
            case "F4": return 3;
            case "F5": return 4;
            case "F6": return 5;
            default: return 6;
        }
    }
}

