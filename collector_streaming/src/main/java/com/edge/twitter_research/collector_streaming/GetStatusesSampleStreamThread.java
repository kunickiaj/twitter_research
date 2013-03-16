package com.edge.twitter_research.collector_streaming;

import com.edge.twitter_research.core.CrisisMailer;
import twitter4j.TwitterStream;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class GetStatusesSampleStreamThread extends Thread {

    private TwitterStream twitterStream;
    private static Logger logger =
            Logger.getLogger(GetStatusesSampleStreamThread.class);
    private CrisisMailer crisisMailer;

    public GetStatusesSampleStreamThread(TwitterStream twitterStream,
                                         String log4jPropertiesFilePath){
        try{
            this.twitterStream = twitterStream;
            PropertyConfigurator.configure(log4jPropertiesFilePath);
            this.crisisMailer = CrisisMailer.getCrisisMailer();
        }catch (Exception exception){
            logger.error("Exception while setting up GetStatusesSampleStreamThread", exception);
            crisisMailer.sendEmailAlert(exception);
        }
    }

    public void run(){
        try{
            twitterStream.sample();
        }catch (Exception exception){
            logger.error("Exception while sampling twitter stream", exception);
            crisisMailer.sendEmailAlert(exception);
        }
    }

}