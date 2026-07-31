package com.everyarrow.eventengine.boot;

public final class EngineMain {

    private EngineMain() {
    }

    public static void main(String[] args) throws Exception {
        Engine engine = StartUpHelper.createAndInitializeEngine();
        StartUpHelper.startEngineWithHttp(engine);
        StartUpHelper.installShutdownHook(engine);
        StartUpHelper.waitForShutdown(engine);
    }
}
