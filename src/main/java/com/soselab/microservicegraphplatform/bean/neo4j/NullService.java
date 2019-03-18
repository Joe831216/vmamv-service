package com.soselab.microservicegraphplatform.bean.neo4j;

import javax.annotation.Nullable;

public class NullService extends Service {

    public NullService() {
    }

    public NullService(@Nullable String scsName, String appName, @Nullable String version, int number) {
        super(scsName, appName, version, number);
    }

}
