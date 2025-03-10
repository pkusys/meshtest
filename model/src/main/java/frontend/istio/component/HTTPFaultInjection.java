package frontend.istio.component;

import frontend.Config;

public class HTTPFaultInjection extends Config {
    public static class Delay extends Config {
        /**
         * Indicate the amount o delay.
         */
        public String fixedDelay;
        public Wrapped.Percent percentage;
    }

    public Delay delay;

    public static class Abort extends Config {
        /**
         * HTTP status code.
         */
        public int httpStatus = -1;
        /**
         * GRPC status code.
         */
        public String grpcStatus;
        public Wrapped.Percent percentage;
    }

    public Abort abort;
}
