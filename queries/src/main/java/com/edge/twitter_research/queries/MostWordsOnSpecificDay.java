package com.edge.twitter_research.queries;

import com.edge.twitter_research.core.GlobalConstants;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.kiji.mapreduce.KijiMapReduceJob;
import org.kiji.mapreduce.KijiMapReduceJobBuilder;
import org.kiji.mapreduce.gather.KijiGatherJobBuilder;
import org.kiji.mapreduce.input.AvroKeyValueMapReduceJobInput;
import org.kiji.mapreduce.input.SequenceFileMapReduceJobInput;
import org.kiji.mapreduce.output.AvroKeyValueMapReduceJobOutput;
import org.kiji.mapreduce.output.SequenceFileMapReduceJobOutput;
import org.kiji.mapreduce.output.TextMapReduceJobOutput;
import org.kiji.schema.KijiURI;
import org.kiji.schema.filter.HasColumnDataRowFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;



public class MostWordsOnSpecificDay extends Configured {

    public ArrayList<KijiMapReduceJob> mapReduceJobs;

    public static Logger logger =
            Logger.getLogger(PerTimeTweetVolumeForAllCompanies.class);

    public MostWordsOnSpecificDay (String rootFilePath,
                                              String inputTableName,
                                              int monthOfYear,
                                              int dayOfMonth){

        mapReduceJobs = new ArrayList<KijiMapReduceJob>();

        PropertyConfigurator.configure(Constants.LOG4J_PROPERTIES_FILE_PATH);

        try{
            Configuration hBaseConfiguration =
                    HBaseConfiguration.addHbaseResources(new Configuration(true));
            hBaseConfiguration.setInt("month.of.year", monthOfYear);
            hBaseConfiguration.setInt("day.of.month", dayOfMonth);


            //hBaseConfiguration.set("mapred.textoutputformat.separator", ",");
            //hBaseConfiguration.setInt("hbase.client.scanner.caching", 1000);

            Path intermediateFilePath = new Path(rootFilePath + "/intermediate");
            Path resultFilePath = new Path(rootFilePath + "/result/" + monthOfYear+ "_" +dayOfMonth );

            KijiURI tableUri =
                    KijiURI.newBuilder(String.format("kiji://.env/default/%s", inputTableName)).build();

            String additionalJarsPath = "";
            try{
                additionalJarsPath = InetAddress.getLocalHost().getHostName().equals("master")?
                        GlobalConstants.ADDTIONAL_JARS_PATH_KIJI_CLUSTER :
                        GlobalConstants.ADDTIONAL_JARS_PATH_BENTO;
            }catch (UnknownHostException unknownHostException){
                logger.error(unknownHostException);
                unknownHostException.printStackTrace();
                System.exit(-1);
            }


            FileSystem fs = FileSystem.newInstance(hBaseConfiguration);

            if (!fs.exists(intermediateFilePath)){

                HasColumnDataRowFilter filter =
                        new HasColumnDataRowFilter(GlobalConstants.TWEET_OBJECT_COLUMN_FAMILY_NAME,
                                GlobalConstants.COMPANY_DATA_COLUMN_NAME);

                mapReduceJobs.add(KijiGatherJobBuilder.create()
                        .withConf(hBaseConfiguration)
                        .withGatherer(SpecificDateGatherer.class)
                        .withFilter(filter)
                        .withReducer(WordCounter.class)
                        .withInputTable(tableUri)
                        .withOutput(new SequenceFileMapReduceJobOutput(intermediateFilePath, 12))
                        .addJarDirectory(new Path(additionalJarsPath))
                        .build());
            }


            mapReduceJobs.add(KijiMapReduceJobBuilder.create()
                    .withConf(hBaseConfiguration)
                    .withMapper(CountedWordsMapper.class)
                    .withReducer(CountedWordsSorter.class)
                    .withInput(new SequenceFileMapReduceJobInput(intermediateFilePath))
                    .withOutput(new TextMapReduceJobOutput(resultFilePath, 1))
                    .addJarDirectory(new Path(additionalJarsPath))
                    .build());


        }catch (IOException ioException){
            logger.error("IO Exception while configuring MapReduce Job", ioException);
            System.exit(1);
        } catch (Exception unknownException){
            logger.error("Unknown Exception while configuring MapReduce Job", unknownException);
            System.exit(1);
        }
    }


    public static void main(String[] args){

        if (args.length < 3){
            System.out.println("Usage: MostWordsOnSpecificDay " +
                    "<HDFS_job_root_file_path> " +
                    "<month_of_year> " +
                    "<day_of_month>");
            return;
        }


        String inputTableName = "filter_tweet_store";
        String HDFSjobRootFilePath = args[0];
        int monthOfYear = Integer.parseInt(args[1]);
        int dayOfMonth = Integer.parseInt(args[2]);


        MostWordsOnSpecificDay mostWordsOnSpecificDay =
                new MostWordsOnSpecificDay(HDFSjobRootFilePath,
                        inputTableName,
                        monthOfYear,
                        dayOfMonth);


        boolean isSuccessful = false;

        for (KijiMapReduceJob mapReduceJob : mostWordsOnSpecificDay.mapReduceJobs){

            if (mapReduceJob != null){
                try{
                    isSuccessful = mapReduceJob.run();
                    if (!isSuccessful)
                        break;
                }catch (Exception unknownException){
                    logger.error("Unknown Exception while running MapReduce Job", unknownException);
                    System.exit(1);
                }
            }
        }

        String result = isSuccessful ? "Successful" : "Failure";
        logger.info(result);
    }

}

