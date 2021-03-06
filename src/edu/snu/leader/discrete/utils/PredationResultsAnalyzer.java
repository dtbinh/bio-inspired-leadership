/*
 * The Bio-inspired Leadership Toolkit is a set of tools used to simulate the
 * emergence of leaders in multi-agent systems. Copyright (C) 2014 Southern
 * Nazarene University This program is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or at your option) any later version. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package edu.snu.leader.discrete.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * PredationResultsAnalyzer A utility class that analyzes the results from
 * predation
 * 
 * @author Tim Solum
 * @version $Revision$ ($Author$)
 */
public class PredationResultsAnalyzer
{
    public static void main( String[] args )
    {
        final String RESULTS_FOLDER = "results_global_agent-count=10_SueurValues";

        // Choose which rfile to make
        boolean meanTime = false;
        boolean meanEaten = false;
        boolean constantsVsTime = false;

        // the primary rfile stringbuilder
        StringBuilder rfileBuilder = new StringBuilder();
        // all of the stringbuilders used to construct the rfiles, these will be
        // r vectors
        StringBuilder constantBuilder = new StringBuilder();
        StringBuilder meanEatenBuilder = new StringBuilder();
        StringBuilder meanTimeBuilder = new StringBuilder();
        StringBuilder timeConstantsBuilder = new StringBuilder();
        StringBuilder timeTimesBuilder = new StringBuilder();

        // setup stringbuilders that will be holding r vectors
        constantBuilder.append( "constants <- c(" );
        meanEatenBuilder.append( "meanEaten <- c(" );
        meanTimeBuilder.append( "meanTime <- c(" );
        timeConstantsBuilder.append( "timeConstants <- c(" );
        timeTimesBuilder.append( "timeTimes <- c(" );

        // the folder where our results will be found
        File folder = new File( RESULTS_FOLDER );
        File[] listOfFiles = folder.listFiles();

        // two maps that will be used to help make the r vectors in the string
        // builders
        Map<Double, PredationContainer> predationEvents = new HashMap<Double, PredationContainer>();
        Map<Double, TimeContainer> predationTimes = new HashMap<Double, TimeContainer>();

        // three regex to parse through results files
        final String runRegex = "run-count=([0-9]+)";
        final String constantRegex = "predation-constant=([0-9].[0-9]+E-[0-9]+|[0-9]+.[0-9]+)";
        final String meanEatenRegex = "([0-9]{3})[\\s]*([0-9]{6})[\\s]*(P-D)[\\s]*(Ind[0-9]{5})[\\s]*([0-9]{3})[\\s]*(Group[0-9]{1,3})";

        for( int i = 0; i < listOfFiles.length; i++ )
        {
            if( listOfFiles[i].getName().contains( "pred_const" ) )
            {
                // scanner and regex objects
                Scanner scanner = null;
                Pattern pattern = null;
                Matcher matcher = null;

                // open a new file
                StringBuilder b = new StringBuilder();
                try
                {
                    scanner = new Scanner( new File(
                            listOfFiles[i].getAbsolutePath() ) );
                }
                catch( FileNotFoundException e )
                {
                    e.printStackTrace();
                }

                // get data from file
                while( scanner.hasNextLine() )
                {
                    b.append( scanner.nextLine() + "\n" );
                }

                // put the raw results file into a string
                String rawData = b.toString();

                System.out.println( listOfFiles[i].getName() );

                // our temporary predation container
                PredationContainer temp = new PredationContainer();

                // run regex
                pattern = Pattern.compile( runRegex );
                matcher = pattern.matcher( rawData );
                if( matcher.find() )
                {
                    // set run count and simulations per run
                    int runCount = Integer.parseInt( matcher.group( 1 ) );
                    temp.totalRuns = runCount;
                    temp.simulationsPerRun = runCount;
                }

                // constant regex
                pattern = Pattern.compile( constantRegex );
                matcher = pattern.matcher( rawData );
                if( matcher.find() )
                {
                    // set predation constant
                    double predationConstant = Double.parseDouble( matcher.group( 1 ) );
                    temp.predationConstant = predationConstant;
                }

                // mean eaten and time regex
                pattern = Pattern.compile( meanEatenRegex );
                matcher = pattern.matcher( rawData );
                int count = 0;
                while( matcher.find() )
                {
                    // run information
                    int run = Integer.parseInt( matcher.group( 1 ) );
                    // group size information
                    int n = Integer.parseInt( matcher.group( 5 ) );

                    // increment total eaten and total time
                    temp.totalEaten += 1;
                    temp.totalTime += Integer.parseInt( matcher.group( 2 ) );
                    // if we have seen this constant before
                    if( predationTimes.containsKey( temp.predationConstant ) )
                    {
                        // add times to predation times
                        predationTimes.get( temp.predationConstant ).times.add( Integer.parseInt( matcher.group( 2 ) ) );
                        // append predation constant to its builder
                        timeConstantsBuilder.append( temp.predationConstant
                                + ", " );
                        // append time to times builder
                        timeTimesBuilder.append( Integer.parseInt( matcher.group( 2 ) )
                                + ", " );
                    }
                    else
                    {
                        // create a new time container
                        TimeContainer timeCont = new TimeContainer();
                        // set predation constant and add time
                        timeCont.predationConstant = temp.predationConstant;
                        timeCont.times.add( Integer.parseInt( matcher.group( 2 ) ) );
                        predationTimes.put( temp.predationConstant, timeCont );
                        // append predation constant to its builder
                        timeConstantsBuilder.append( temp.predationConstant
                                + ", " );
                        // append time to times builder
                        timeTimesBuilder.append( Integer.parseInt( matcher.group( 2 ) )
                                + ", " );
                    }
                    count++;
                    // System.out.println("Run: " + run + "  n: " + n);//debug
                }
                System.out.println( "Mean eaten: " + temp.getMeanEaten() );// debug
                System.out.println( "Mean time: " + temp.getMeanTime() + "\n" );// debug

                // if predation events has this predation constant
                if( predationEvents.containsKey( temp.predationConstant ) )
                {
                    // increment total eaten
                    predationEvents.get( temp.predationConstant ).totalEaten += temp.totalEaten;
                    if( count == 0 )
                    {
                        // set to 1 if its zero to avoid divide be 0 error
                        count = 1;
                    }
                    // add total time and total runs
                    predationEvents.get( temp.predationConstant ).totalTime += temp.totalTime
                            / count;
                    predationEvents.get( temp.predationConstant ).totalRuns += temp.totalRuns;
                }
                else
                {
                    // add new predation constant
                    predationEvents.put( temp.predationConstant, temp );
                }
            }
        }

        // add constants, mean eaten, and mean time to their vector builders
        List<Double> constants = new ArrayList<Double>();
        constants.addAll( predationEvents.keySet() );
        Collections.sort( constants );
        for( int i = 0; i < constants.size(); i++ )
        {
            constantBuilder.append( constants.get( i ) + ", " );
            meanEatenBuilder.append( predationEvents.get( constants.get( i ) ).getMeanEaten()
                    + ", " );
            meanTimeBuilder.append( predationEvents.get( constants.get( i ) ).getMeanTime()
                    + ", " );
        }

        // delete extra characters at end and close off with parenthesis
        constantBuilder.deleteCharAt( constantBuilder.length() - 1 );
        constantBuilder.deleteCharAt( constantBuilder.length() - 1 );
        constantBuilder.append( ")\n" );

        meanEatenBuilder.deleteCharAt( meanEatenBuilder.length() - 1 );
        meanEatenBuilder.deleteCharAt( meanEatenBuilder.length() - 1 );
        meanEatenBuilder.append( ")\n" );

        meanTimeBuilder.deleteCharAt( meanTimeBuilder.length() - 1 );
        meanTimeBuilder.deleteCharAt( meanTimeBuilder.length() - 1 );
        meanTimeBuilder.append( ")\n" );

        timeConstantsBuilder.deleteCharAt( timeConstantsBuilder.length() - 1 );
        timeConstantsBuilder.deleteCharAt( timeConstantsBuilder.length() - 1 );
        timeConstantsBuilder.append( ")\n" );

        timeTimesBuilder.deleteCharAt( timeTimesBuilder.length() - 1 );
        timeTimesBuilder.deleteCharAt( timeTimesBuilder.length() - 1 );
        timeTimesBuilder.append( ")\n" );

        // append all the vectors to the rfile builder
        rfileBuilder.append( constantBuilder.toString() );
        rfileBuilder.append( meanEatenBuilder.toString() );
        rfileBuilder.append( meanTimeBuilder.toString() );
        rfileBuilder.append( timeConstantsBuilder.toString() );
        rfileBuilder.append( timeTimesBuilder.toString() );
        rfileBuilder.append( "\n" );

        // mean eaten r plot
        if( meanEaten )
        {
            rfileBuilder.append( "plot(meanEaten, type=\"o\", col=\"blue\", axes=FALSE, ann=FALSE)\n" );
            rfileBuilder.append( "axis(1, at=1:" + constants.size()
                    + ", constants, las=2)\n" );
            rfileBuilder.append( "axis(2, las=1.0, at=0:10)\n" );
            rfileBuilder.append( "title(main=\"Predation Constants\", col.main=\"red\", fon.main=4)\n" );
            rfileBuilder.append( "title(xlab=\"Predation Constant\", col.lab=rgb(0,0.5,0))\n" );
            rfileBuilder.append( "title(ylab=\"Mean Number Eaten\", col.lab=rgb(0,0.5,0))\n\n" );
        }

        // mean time r plot
        if( meanTime )
        {
            rfileBuilder.append( "plot(meanTime, type=\"o\", col=\"blue\", axes=FALSE, ann=FALSE)\n" );
            rfileBuilder.append( "axis(1, at=1:" + constants.size()
                    + ", constants, las=2)\n" );
            rfileBuilder.append( "axis(2, las=2, at=meanTime)\n" );
            rfileBuilder.append( "title(main=\"Predation Constants\", col.main=\"red\", fon.main=4)\n" );
            rfileBuilder.append( "title(xlab=\"Predation Constant\", col.lab=rgb(0,0.5,0))\n" );
            rfileBuilder.append( "title(ylab=\"Mean Time Taken\", col.lab=rgb(0,0.5,0))\n\n" );
        }

        // constants vs time r plot
        if( constantsVsTime )
        {
            rfileBuilder.append( "plot(timeConstants, timeTimes, axes=FALSE, ann=FALSE)\n" );
            rfileBuilder.append( "axis(1, at=constants, las=2)\n" );
            rfileBuilder.append( "axis(2, las=2, at=500*0:max(timeTimes))\n" );
            rfileBuilder.append( "title(main=\"Predation Constants vs Time\", col.main=\"red\", fon.main=4)\n" );
            rfileBuilder.append( "title(xlab=\"Predation Constant\", col.lab=rgb(0,0.5,0))\n" );
            rfileBuilder.append( "title(ylab=\"Time Taken\", col.lab=rgb(0,0.5,0))\n\n" );
        }

        // write the r builder to an r file
        File file = new File( "predationStuff.R" );
        PrintWriter out = null;

        try
        {
            out = new PrintWriter( new BufferedWriter( new FileWriter( file,
                    true ) ) );
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }

        out.println( rfileBuilder.toString() );
        out.close();

        System.out.println( rfileBuilder.toString() );
    }

    public static class PredationContainer
    {
        public double predationConstant = 0;

        public double totalEaten = 0;

        public int totalTime = 0;

        public int totalRuns = 0;

        public int simulationsPerRun = 0;

        public PredationContainer()
        {
            predationConstant = 0;
            totalEaten = 0;
            totalRuns = 0;
        }

        public double getMeanEaten()
        {
            return totalEaten / totalRuns;
        }

        public double getMeanTime()
        {
            System.out.println( totalTime + "/" + totalRuns );
            return totalTime / totalRuns * simulationsPerRun;
        }
    }

    public static class TimeContainer
    {
        public double predationConstant = 0;

        public List<Integer> times = new LinkedList<Integer>();
    }
}
