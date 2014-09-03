package com.sk.learn;


import ao.ai.classify.online.forest.OnlineRandomForest;
import ao.ai.ml.model.algo.OnlineMultiLearner;
import ao.ai.ml.model.input.RealList;
import ao.ai.ml.model.output.MultiClass;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableTable;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.sk.learn.hash.BinaryNistHasher;
import com.sk.learn.hash.IdentityNistHasher;
import com.sk.learn.hash.SemanticHasher;

import java.io.*;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Main
{
    static final String DELIMITER = ",";
    static final Splitter PARSER = Splitter.on(DELIMITER);
    static final Joiner FORMATTER = Joiner.on(DELIMITER);
    static final int COLUMNS = 8;


    public static void main(String[] args) throws IOException {
        CharSource input = Files.asCharSource(
                new File("D:/Downloads/optdigits.tra"),
                Charsets.UTF_8);

        List<NistInstance> instances = input.readLines().stream().map(line -> {
            List<String> tokens = PARSER.splitToList(line);
            List<Integer> values = tokens.stream().map(Integer::parseInt).collect(Collectors.toList());

            List<Integer> instanceInput = values.subList(0, values.size() - 1);
            int instanceOutput = values.get(values.size() - 1);

            ImmutableTable.Builder<Integer, Integer, Integer> instance = ImmutableTable.builder();
            for (int i = 0; i < instanceInput.size(); i++) {
                int value = instanceInput.get(i);
                int row = i / COLUMNS;
                int column = i % COLUMNS;

                instance.put(row, column, value);
            }

            return NistInstance.create(
                    instanceInput,
                    instance
                            .orderRowsBy(Comparator.<Integer>naturalOrder())
                            .orderColumnsBy(Comparator.<Integer>naturalOrder())
                            .build(),
                    instanceOutput);
        }).collect(Collectors.toList());

        SemanticHasher<NistInstance> hasher =
//                IdentityNistHasher.INSTANCE;
                BinaryNistHasher.INSTANCE;

        File outputPath = new File(String.format("out/nist_%s.csv", hasher));
        Files.createParentDirs(outputPath);

        CharSink output = Files.asCharSink(
                outputPath, Charsets.UTF_8);

        GridLearner gridLearner = new GridLearner();

        AtomicLong count = new AtomicLong();
        try (PrintWriter out = new PrintWriter(output.openBufferedStream()))
        {
//            List<Table<Integer, Integer, Integer>> grids =
//                    StreamSupport.stream(instances.spliterator(), false)
////                    .map(NistInstance::input)
//                    .collect(Collectors.toList());

            OnlineMultiLearner<RealList> learner = new OnlineRandomForest();

            instances.forEach(instance -> {
                out.println("\n\n");

                RealList inputValues = instance.inputRealList();

                MultiClass prediction = learner.classify(inputValues);

                boolean correct =
                        (prediction.best() == instance.output());

                out.println("INFO," + prediction.best());
                out.println("INFO," + instance.output());
                out.println("INFO," + correct);

                learner.learn(inputValues, MultiClass.create(instance.output()));

                RealList semanticHash = hasher.hash(instance);

                gridLearner.learn(semanticHash, instance.input());

                for (Map<Integer, Integer> row : instance.input().rowMap().values()) {
                    String rowOutput = FORMATTER.join(row.values());
                    out.println(rowOutput);
                }

                out.println();

                for (Map<Integer, Integer> row : gridLearner.predict(semanticHash).rowMap().values()) {
                    String rowOutput = FORMATTER.join(row.values());
                    out.println(rowOutput);
                }

                if (count.incrementAndGet() % 100 == 0) {
                    System.out.println(count.longValue() + "\t" + LocalDateTime.now());
                }
            });
        }
    }
}