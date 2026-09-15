public class Main {

    public static void main(String[] args) {

        Mag8000Connect mag =
                new Mag8000Connect();

        try {

            mag.connect();

            System.out.println();
            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "          MAG-8000 DATA"
            );

            System.out.println(
                    "======================================"
            );

            for (DataInfo register
                    : Mag8000Data.IMPORTANT_REGISTERS) {

                mag.printRegister(
                        register
                );
            }

            System.out.println(
                    "======================================"
            );

        } finally {

            mag.close();
        }
    }
}