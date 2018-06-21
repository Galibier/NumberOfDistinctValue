import java.util.HashSet;

public class HyperLogLog {

    public static final int AMOUNT_OF_REGISTERS = 16384;
    public static final int HLL_P_MASK = 16383;

    public static int evaluationOfDistinctValue(byte[][] InputData){
        byte registersForCount[] = new byte[AMOUNT_OF_REGISTERS];
        int distinctValue;
        int dataSize = InputData.length;

        for (int i=0; i<dataSize; i++){
            long hashCodeOfSingleData = MurmurHash.hash64(InputData[i]);
            hyperLogLogAddRegisters(hashCodeOfSingleData, registersForCount);
        }

        distinctValue = hyperLogLogCount(registersForCount);

        return distinctValue;
    }

    /**
     * fill the registers according to the hash code the single data generate
     *
     * @param hashCodeOfSingleData  hash code one single data generate
     *
     * @param RegistersForCount  registers for counting the first bit of ones
     */

    public static void hyperLogLogAddRegisters(long hashCodeOfSingleData, byte[] RegistersForCount){
        int index = (int)(hashCodeOfSingleData & HLL_P_MASK);
        hashCodeOfSingleData |= ((long)1 << 63); /* Make sure the loop terminates. hashCode is all 0*/
        long bit = AMOUNT_OF_REGISTERS; /* First bit not used to address the register. */
        byte firstBitOfOne = 1;

        while ((hashCodeOfSingleData & bit) == 0) {
            firstBitOfOne++;
            bit <<= 1;
        }

        if(RegistersForCount[index] < firstBitOfOne)
            RegistersForCount[index] = firstBitOfOne;
    }

    /**
     * Return the approximated distinct value of the set based on the armonic
     * mean of the registers values.
     *
     * @param RegistersForCount  registers count the first bit of one
     *
     * @return 64 bit hash of the given string
     */
    public static int hyperLogLogCount(byte[] RegistersForCount) {
        double amountOfRegisters = AMOUNT_OF_REGISTERS;
        double E = 0, alpha = 0.7213 / (1 + 1.079 / amountOfRegisters);
        int j, RegistersOfZero = 0; /* Number of registers equal to 0. */

        /* We precompute 2^(-reg[j]) in a small table in order to
         * speedup the computation of SUM(2^-register[0..i]). */
        double[] preComputeForEvaluation = new double[64];

        preComputeForEvaluation[0] = 1; /* 2^(-reg[j]) is 1 when m is 0. */
        for (j = 1; j < 64; j++) {
            preComputeForEvaluation[j] = 1.0 / (1L << j); /* 2^(-reg[j]) is the same as 1/2^reg[j]. */
        }

        /* Compute SUM(2^-register[0..i]). */
        for (byte n : RegistersForCount) {
            E += preComputeForEvaluation[n];
            if (n == 0)
                RegistersOfZero++;
        }

        /* Muliply the inverse of E for alpha_m * m^2 to have the raw estimate. */
        E = (1 / E)*alpha*amountOfRegisters*amountOfRegisters;

        /* Use the LINEARCOUNTING algorithm for small cardinalities.
         * For larger values but up to 72000 HyperLogLog raw approximation is
         * used since linear counting error starts to increase. However HyperLogLog
         * shows a strong bias in the range 2.5*16384 - 72000, so we try to
         * compensate for it. */
        if (E < amountOfRegisters*2.5 && RegistersOfZero != 0) {
            E = amountOfRegisters * Math.log(amountOfRegisters / RegistersOfZero); /* LINEARCOUNTING() */
        }
        else if (amountOfRegisters == 16384 && E < 72000) {
            /* We did polynomial regression of the bias for this range, this
             * way we can compute the bias for a given cardinality and correct
             * according to it. Only apply the correction for P=14 that's what
             * we use and the value the correction was verified with. */
            double bias = 5.9119*1.0e-18*(E*E*E*E)
                    - 1.4253*1.0e-12*(E*E*E) +
                    1.2940*1.0e-7*(E*E)
                    - 5.2921*1.0e-3*E +
                    83.3216;
            E -= E * (bias / 100);
        }

        return (int)E;
    }

    public static void main(String[] args) {
        /*
            Some Testing Example may output like this:
            lengthOfSingleData: 21 >>> ActualCount: 896293  Evaluation: 903195     time：31 ms    bias: 0.77%
            lengthOfSingleData: 22 >>> ActualCount: 901216  Evaluation: 894688     time：32 ms    bias: -0.72%
            lengthOfSingleData: 23 >>> ActualCount: 905662  Evaluation: 905602     time：32 ms    bias: -0.01%
            lengthOfSingleData: 24 >>> ActualCount: 910509  Evaluation: 907605     time：46 ms    bias: -0.32%
            lengthOfSingleData: 25 >>> ActualCount: 914013  Evaluation: 910035     time：31 ms    bias: -0.44%
            lengthOfSingleData: 26 >>> ActualCount: 916874  Evaluation: 926174     time：31 ms    bias: 1.01%
            lengthOfSingleData: 27 >>> ActualCount: 920922  Evaluation: 906198     time：31 ms    bias: -1.60%
            lengthOfSingleData: 28 >>> ActualCount: 923872  Evaluation: 932382     time：32 ms    bias: 0.92%
            lengthOfSingleData: 29 >>> ActualCount: 926440  Evaluation: 941870     time：31 ms    bias: 1.67%
            lengthOfSingleData: 30 >>> ActualCount: 928566  Evaluation: 924608     time：31 ms    bias: -0.43%
         */
        
        /*can be change to test different amount of data*/
        int amountOfData = 1000000;
        int maxSingleDataLength = 128;

        for (int singleDataLength=2; singleDataLength<maxSingleDataLength; singleDataLength++) {
            HashSet hs = new HashSet();
            byte[][] testData2 = new byte[amountOfData][];
            for (int n = 0; n < amountOfData; n++) {
                int len = (int) (Math.random() * singleDataLength);
                //System.out.println(len);
                testData2[n] = new byte[len];
                for (int i = 0; i < len; i++) {
                    testData2[n][i] = (byte) (Math.random() * 12354791);
                }
                hs.add(MurmurHash.hash64(testData2[n]));
            }
            System.out.printf("lengthOfSingleData: %d >>> ", singleDataLength-1);
            System.out.printf("ActualCount: %d  ", hs.size());

            long startTime=System.currentTimeMillis();
            int evaluationOfDistinctValues = evaluationOfDistinctValue(testData2);
            long endTime=System.currentTimeMillis();

            System.out.printf("Evaluation: %d  ", evaluationOfDistinctValues);
            System.out.printf("   time：%d ms ", (endTime-startTime));
            System.out.printf("   bias: %.2f%%\n", 100*(evaluationOfDistinctValues-hs.size())/(double)hs.size());
        }
    }
}