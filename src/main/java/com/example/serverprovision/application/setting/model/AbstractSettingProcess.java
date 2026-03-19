package com.example.serverprovision.application.setting.model;

public abstract class AbstractSettingProcess implements Comparable<AbstractSettingProcess> {

    private final int PROCESSING_ORDER;

    protected AbstractSettingProcess(int processingOrder) {
        this.PROCESSING_ORDER = processingOrder;
    }

    public final int getProcessingOrder() {
        return PROCESSING_ORDER;
    }

    @Override
    public final int compareTo(AbstractSettingProcess o) {
        return Integer.compare(this.PROCESSING_ORDER, o.PROCESSING_ORDER);
    }
}
