package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class DiffStatsDTO {

    private int oracleAdded;
    private int oracleRemoved;
    private int gaussAdded;
    private int gaussRemoved;
}
