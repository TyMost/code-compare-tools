package com.example.migratediff.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    private String id;
    private String action;
    private String operator;
    private LocalDateTime timestamp;
    private String details;
}
