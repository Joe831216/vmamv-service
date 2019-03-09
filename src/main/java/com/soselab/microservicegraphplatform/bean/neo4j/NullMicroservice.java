package com.soselab.microservicegraphplatform.bean.neo4j;

import javax.annotation.Nullable;

public class NullMicroservice extends Microservice {

    public NullMicroservice() {
    }

    public NullMicroservice(String scsName, String appName, @Nullable String version) {
        super(scsName, appName, version);
    }

}
