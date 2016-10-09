package com.mihirjoshi.ocr.controller;

import com.squareup.otto.Bus;

/**
 * Static class for Event bus
 */
public final class MyBus {
    private static final Bus BUS = new Bus();

    public static Bus getInstance() {
        return BUS;
    }

    private MyBus() {
    }
}
