package com.soselab.microservicegraphplatform.bean.neo4j;

import javax.annotation.Nullable;

public class NullService extends Service {

    public NullService() {
    }

    public NullService(String scsName, String appName, @Nullable String version) {
        super(scsName, appName, version);
    }

}
