package com.advantek.suivieSequence.model;

import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Estimation {
    private List<LocalDate> dates;
    private String cdrType;
    private List<Double> totals;

    public Estimation(String cdrType, List<CdrFilesSurvey> cdrFilesSurveyList) {
        this.dates = cdrFilesSurveyList.stream().map(CdrFilesSurvey::getDate).collect(Collectors.toList());
        this.cdrType = cdrType;
        this.totals = cdrFilesSurveyList.stream()
                .map(item -> item.getCount().add(item.getMissing()))
                .map(Number::doubleValue)
                .collect(Collectors.toList());
    }

    public double getMoyenne(){
        if (!totals.isEmpty()) {
            return this.totals.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
        }
        else {
            return 0.0;
        }
    }

    public double getEcartType(double moyenne) {
        if((moyenne != 0.0) && (!totals.isEmpty())){
            double variance = this.totals.stream()
                    .mapToDouble(item -> Math.pow((item - moyenne), 2))
                    .average()
                    .orElse(0.0);
            return Math.sqrt(variance);
        }
        else {
            return 0.0;
        }
    }

    public List<BigDecimal> getEstimationInterval(double ecartType, double moyenne, int facteur){
        List<BigDecimal> interval = new ArrayList<>();
        interval.add(BigDecimal.valueOf(moyenne - (facteur*ecartType)).setScale(0, RoundingMode.HALF_DOWN));
        interval.add(BigDecimal.valueOf(moyenne + (facteur*ecartType)).setScale(0, RoundingMode.HALF_DOWN));
        return interval;
    }

    public List<BigDecimal> getEstimationInterval(int facteur) {
        double moyenne = this.getMoyenne();
        double ecartType = this.getEcartType(moyenne);
        return this.getEstimationInterval(ecartType, moyenne, facteur);
    }
}
