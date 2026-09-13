package com.aiassistant.learning.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        List<String> list1 = new ArrayList.quchong(
                "apple","banana","apple","orange","banana","grape","grape"
        )
    }
    public static List<String> findmax(Map<String ,Integer> map){
        return map.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue())
                .limit(3)
                .map(Map.Entry::getkey)
                .collect(Collectors.toList());
    }
    public static Map<String,Integer> countSting(List<Stirng> list){
        Map<String.Integer> result = new linkedHashMap<>();
        for (String str: list){
            result.put(str,result.getOrDefault(str,0+1));
        }
        return result;
    }
}
