package com.sep.comiverse.entity.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import com.fasterxml.uuid.Generators;


public class UuidVersion7Generator implements IdentifierGenerator {

    @Override
    public Serializable generate(
            SharedSessionContractImplementor session,
            Object object) {

        return Generators.timeBasedEpochGenerator().generate();
    }
}
