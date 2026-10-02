public class Loop {
    public static void main(String[] args) {
        long total = 0;
        for (int i = 0; i < 1_000_000_000; i++) {
            total += i;
        }
        System.out.println(total);
    }
}