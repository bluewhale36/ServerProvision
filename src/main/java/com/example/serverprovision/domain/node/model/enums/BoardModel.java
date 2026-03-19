package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.example.serverprovision.domain.node.model.enums.Vendor.GIGABYTE;

@RequiredArgsConstructor
@Getter
public enum BoardModel {
    MS03_CE0("MS03-CE0-000", GIGABYTE),
    MS73_HB1("MS73-HB1-000", GIGABYTE);

    private final String modelNameInternal;
    private final Vendor vendor;

    public static BoardModel getBoardModelByString(String modelName, Vendor vendor) {
        for (BoardModel boardModel : BoardModel.values()) {
            if (
                    boardModel.getModelNameInternal().contains(modelName.toUpperCase()) ||
                    boardModel.name().contains(modelName.toUpperCase())
            ) {
                if (boardModel.getVendor() == vendor) {
                    return boardModel;
                }
            }
        }
        return null;
    }
}
