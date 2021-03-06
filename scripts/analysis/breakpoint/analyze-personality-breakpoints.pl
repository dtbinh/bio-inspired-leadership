#!/usr/bin/perl
use strict;
use POSIX;
use List::Util qw[min max];

# -------------------------------------------------------------------
use constant BOLDTHRESHOLD => 0.775;
use constant PVBUFFERPERCENTAGE => 0.85;
use constant MINTHRESHOLDIDX => 500;

# -------------------------------------------------------------------
# Get the data directory
my $dataDir = shift( @ARGV );

# Get the data output file prefix
my $outputFilePrefix = shift( @ARGV );

# Get the eps file prefix
my $epsFilePrefix = shift( @ARGV );

# -------------------------------------------------------------------
# Get each of the data files
opendir( DIR, $dataDir ) or die "Unable to open data directory [$dataDir]: $!\n";
my @dataFiles = grep { /\.dat$/ } readdir( DIR );
@dataFiles = sort( @dataFiles );
closedir( DIR );

#print "Found ",($#dataFiles + 1)," files\n";


# -------------------------------------------------------------------
# Process each data files
foreach my $dataFile (@dataFiles)
{
    processDataFile( "$dataDir/$dataFile", $outputFilePrefix, $epsFilePrefix );
}


# ===================================================================
sub processDataFile
{
    my ($dataFile, $dataOutputPrefix, $epsOutputPrefix) = @_;

    # Load the data
    my %data;
    $data{"datafile"} = $dataFile;
    loadData( $dataFile, \%data );

    # Analyze it
    analyzeData( \%data, $dataOutputPrefix, $epsOutputPrefix );
}

# ===================================================================
sub loadData
{
    my ($dataFile, $dataRef) = @_;

    # Open the file
    open( DATA, "$dataFile" ) or die "Unable to open data file [$dataFile]: $!\n";

#print "    Loading data from [$dataFile]\n";

    # Get the run ID
    $dataFile =~ /var-(\d\d)-seed/;
    my $runID = $1;
    $dataRef->{"run-id"} = $runID;

    # Read each line
    my %rawData;
    while( <DATA> )
    {
        # Remove any other comments or whitespace
        s/#.*//;
        s/^\s+//;
        s/\s+$//;
        next unless length;

        my ($key,$value) = split( /\s+=\s+/, $_ );

        if( $key =~ /^total-simulations$/ )
        {
            $dataRef->{"total-simulations"} = $value;
        }
        elsif( $key =~ /initiation-history/ )
        {
            push( @{$dataRef->{"initiation-history"}}, $value );

            # Process the history data
            my @simIDXs;
            my @personalityValues;
            processHistoryData( $value,
                    $dataRef->{"total-simulations"},
                    \@simIDXs,
                    \@personalityValues );
            
            # Get the individual's ID
            $key =~ /Ind(\d+)/;
            my $indID = $1;

            # Store the data
            $dataRef->{"history"}{$indID}{"sim-indices"} = \@simIDXs;
            $dataRef->{"history"}{$indID}{"personality-values"} = \@personalityValues;
        }
    }

    # Close the file
    close( DATA );
}

# ===================================================================
sub processHistoryData
{
    my ($historyData, $totalSimulations, $simIDXsRef, $personalityValuesRef) = @_;

    # Clean up and split the history
    $historyData =~ s/^\s+\[//;
    $historyData =~ s/\]\s+$//;
    my @initiations = split( /\]\s+\[/, $historyData );

    # If it wasn't the first initiator, go ahead and add the initial personality
    my ($firstIdx, $initialPersonality, $trash) = split( /\s+/, $historyData );
    push( @{$simIDXsRef}, 0 );
    push( @{$personalityValuesRef}, $initialPersonality );

    # Go through all the initiations
    my $lastPersonality;
    foreach my $initiation (@initiations)
    {
        my ($idx, $beforePersonality, $result, $afterPersonality, $followers) =
                split( /\s+/, $initiation );
        push( @{$simIDXsRef}, ($idx + 1) );
        push( @{$personalityValuesRef}, $afterPersonality );
        $lastPersonality = $afterPersonality;
    }
#    push( @{$simIDXsRef}, $totalSimulations );
#    push( @{$personalityValuesRef}, $lastPersonality );
}



# ===================================================================
sub analyzeData
{
    my ($dataRef, $dataOutputPrefix, $epsOutputPrefix) = @_;

    my $plotRunID = $$;

    # ---------------------------------------------------------------
    my $rSpacer = "# =====================================\n";
    my $rCatSpacer = "cat(\"# =====================================\\n\")\n";

    # ---------------------------------------------------------------
    # Create an input file for R
#    my $rInputFile = "/tmp/personality-TEST.r";
    my $rInputFile = "/tmp/personality-$plotRunID.r";
    my $rOutputFile = $rInputFile.".out";

    open( INPUT, "> $rInputFile" ) or die "Unable to open input file [$rInputFile]: $!\n";

    print INPUT "library(strucchange)\n\n";
    print INPUT $rSpacer;
    print INPUT "analyzedinitiations <- 0\n\n";
    print INPUT $rSpacer;

    # ---------------------------------------------------------------
    # Add the data for each individual
    my %usingMeaningful;
    foreach my $indID (sort (keys %{$dataRef->{"history"}} ) )
    {
        # Build the data IDs
        my $simIndexID = "simindex".$indID;
        $dataRef->{"history"}{$indID}{"sim-indices-ID"} = $simIndexID;
        my @simIndices = @{$dataRef->{"history"}{$indID}{"sim-indices"}};
        my $personalityValID = "simpersonality".$indID;
        my @personalityValues = @{$dataRef->{"history"}{$indID}{"personality-values"}};
        $dataRef->{"history"}{$indID}{"personality-values-ID"} = $personalityValID;
        my $tsID = "ts".$indID;
        $dataRef->{"history"}{$indID}{"time-series-ID"} = $tsID;
        $dataRef->{"history"}{$indID}{"updates"} = $#simIndices + 1;
        
        # Extract the meaningful personality values
        my $meaningfullPersonalityValuesRef = extractMeaningfulPersonalityValues(
                BOLDTHRESHOLD,
                \@personalityValues );
        my @meaningfullPersonalityValues = @{$meaningfullPersonalityValuesRef};
        if( (scalar @meaningfullPersonalityValues) < (scalar @personalityValues) )
        {
            $usingMeaningful{$tsID} = 1;
            $dataRef->{"history"}{$indID}{"meaningful-personality-values-end"} =
                    (scalar @meaningfullPersonalityValues);
        }

        # Dump the data into variables
        print INPUT $rSpacer;
        print INPUT "$simIndexID <- scan()\n";
        print INPUT "@simIndices\n\n";
        print INPUT "$personalityValID <- scan()\n";
        print INPUT "@personalityValues\n\n";
        print INPUT $personalityValID,"short <- scan()\n";
        print INPUT "@meaningfullPersonalityValues\n\n";
        print INPUT "$tsID <- ts( data=$personalityValID, start=1 )\n\n";
        print INPUT $tsID,"short <- ts( data=",$personalityValID,"short, start=1 )\n\n";
    }

    # ---------------------------------------------------------------
    # Start sending output to the results file
    my $resultsFile = $dataOutputPrefix.
            "-breakpoint-analysis-".
            $dataRef->{"run-id"}.
            ".dat";

#print "Processing [$resultsFile]\n";
#if( -e $resultsFile )
#{
#    print "BAILING!\n";
#    exit;
#}

    print INPUT "sink(\"$resultsFile\")\n";
    print INPUT "$rCatSpacer";
    print INPUT "cat(\"# run-id=[",$dataRef->{"run-id"},"]\\n\")\n";
    print INPUT "cat(\"# data-file=[",$dataRef->{"datafile"},"]\\n\")\n";
    print INPUT "cat(\"\\n\")\n";
    print INPUT "$rCatSpacer";
    print INPUT "cat(\"total-simulations = ",$dataRef->{"total-simulations"},"\\n\")\n";
    print INPUT "cat(\"\\n\")\n";
    print INPUT "sink()\n";

    my $breakpointDataDir = $dataOutputPrefix."-breakpoint-analysis-objects";
    unless( -d $breakpointDataDir )
    {
        `mkdir $breakpointDataDir`;
    }

    # ---------------------------------------------------------------
    # Analyze the time series for each individual
    my @epsFiles;
    foreach my $indID (sort (keys %{$dataRef->{"history"}} ) )
    {
        # Build the filename
        my $epsFile = $epsOutputPrefix."-breakpoints-run-".
                $dataRef->{"run-id"}.
                "-ind-".
                $indID.
                ".eps";
        push( @epsFiles, $epsFile );
        my $saveFile = $breakpointDataDir.
                "/breakpoint-object-run-".
                $dataRef->{"run-id"}.
                "-ind-".
                $indID.
                ".rdata";


        print INPUT "ptm <- proc.time()\n";

        print INPUT "sink(\"$resultsFile\", append=TRUE)\n";
        print INPUT "$rCatSpacer";
        print INPUT "cat(\"# ind=[$indID]\\n\")\n";
        print INPUT "cat(\"# eps-file=[$epsFile]\\n\")\n";

        # Get the total number of initiations
        print INPUT "cat(\"ind$indID.updates = ",
                $dataRef->{"history"}{$indID}{"updates"},
                "\\n\")\n";

        # Get the time series ID
        my $tsID = $dataRef->{"history"}{$indID}{"time-series-ID"};

        # Get the breakpoints
        print INPUT "bp.$tsID <- breakpoints( ",$tsID,"short ~ 1, h=0.1, breaks=5 )\n";
        print INPUT "cat(\"ind$indID.breakpoints = \", format( bp.$tsID\$breakpoints ), \"\\n\")\n";

        # Get the breakdates
#        print INPUT "bd.$tsID <- breakdates( bp.$tsID )\n";
#        print INPUT "cat(\"$tsID.breakdates = \", format( bd.$tsID ), \"\\n\")\n";

        # Print the simulations
        print INPUT "cat(\"ind$indID.breaksims = \", format( ",
            $dataRef->{"history"}{$indID}{"sim-indices-ID"},
            "[bp.$tsID\$breakpoints] ), \"\\n\")\n";

        # Get the break factor
        print INPUT "bf.$tsID <- breakfactor( bp.$tsID )\n";

        # Fit a linear model to each segment
        print INPUT "flm.$tsID <- lm( ".$tsID."short ~ bf.$tsID - 1 )\n";
        print INPUT "cat(\"ind$indID.coefficients = \", format( as.numeric( coef( flm.$tsID ) ) ), \"\\n\")\n";

        print INPUT "cat(\"\\n\")\n";
        print INPUT "sink()\n";

        # Save the breakpoints to a file
        print INPUT "tsID=$tsID\n";
        print INPUT "save( bp.$tsID, bf.$tsID, flm.$tsID, file=\"$saveFile\" )\n";

        # What is the plot colors?
        my $bpColor = "#6666ff";
        my $trendsColor = "#0000ff";
        my @personalityValues = @{$dataRef->{"history"}{$indID}{"personality-values"}};
        if( $personalityValues[$#personalityValues] > 0.6 )
        {
            $bpColor = "#ff6666";
            $trendsColor = "#ff0000";
        }


        # Plot it out
#        print INPUT "postscript( file=\"$epsFile\", height=4.5, width=5.5, onefile=FALSE, pointsize=12, horizontal=FALSE, paper=\"special\", colormodel=\"rgb\" )\n";
#        print INPUT "par(mar=c(4,4,4,4))\n";
#        print INPUT "plot( $tsID, type=\"l\", ylim=c(0,1), col=\"#444444\",",
#                " ylab=\"Personality\", xlab=\"Personality Updates\" )\n";
##                " ylab=\"Personality\", xlab=\"Updates\" )\n";
#        print INPUT "lines( bp.$tsID, col=\"$bpColor\" )\n";
#        print INPUT "lines( ts( fitted( flm.$tsID ), start=0), col=\"$trendsColor\", lwd=2 )\n";

#        # Are we using less than the complete data?
#        if( $usingMeaningful{$tsID} )
#        {
#            # Yup, extend the line out
#            my $meaningfulEnd = $dataRef->{"history"}{$indID}{"meaningful-personality-values-end"};
#            print INPUT "xExtended = c($meaningfulEnd, length($tsID))\n";
#            print INPUT "yExtended = c( flm.$tsID\$coefficients[length(flm.$tsID\$coefficients)],
#                    flm.$tsID\$coefficients[length(flm.$tsID\$coefficients)] )\n";
#            print INPUT "lines( x=xExtended, y=yExtended, col=\"$trendsColor\", lwd=2 )\n";
#        }

##        print INPUT "ticklocations <- axTicks(1)\n";
#        print INPUT "axis( 4 )\n";
#        print INPUT "mtext( \"Shy\", side=2, line=2, adj=0.0 )\n";
#        print INPUT "mtext( \"Shy\", side=4, line=2, adj=0.0 )\n";
#        print INPUT "mtext( \"Bold\", side=2, line=2, adj=1.0 )\n";
#        print INPUT "mtext( \"Bold\", side=4, line=2, adj=1.0 )\n";
#        print INPUT "dev.off()\n\n";

        print INPUT "proc.time() - ptm\n";

    }

    # ---------------------------------------------------------------
    # Close the input file
    close( INPUT );

    # ---------------------------------------------------------------
    # Run it
    `R --no-save < $rInputFile > $rOutputFile 2>&1`;

    # ---------------------------------------------------------------
    # Clean up the eps files
    my $tmpEPSFile = "/tmp/personality-$plotRunID.eps";
    my $fixedTmpEPSFile = "/tmp/fixed-personality-$plotRunID.eps";
    foreach my $epsFile (@epsFiles)
    {
        if( -e $epsFile )
        {
            `cp $epsFile $tmpEPSFile`;
            `./replace-fonts-in-eps.pl $tmpEPSFile $fixedTmpEPSFile`;
            `/usr/bin/epstool --copy --bbox $fixedTmpEPSFile $epsFile`;
            `epstopdf $epsFile`;
        }
    }

}
# ===================================================================
sub extractMeaningfulPersonalityValues
{
    my ($threshold, $personalityValuesRef) = @_;

    # Convert it to an array
    my @personalityValues = @{$personalityValuesRef};

    # Look backwards to find the last update that had a personality at or below the threshold
    my $thresholdIndex = $#personalityValues;
    my $lastMaxPersonalityIndex = $#personalityValues;
    my $found = 0;
    while( !($found) && ($thresholdIndex >= 0) )
    {
        # Is it the max personality value?
        if( $personalityValues[$thresholdIndex] == 0.9 )
        {
            $lastMaxPersonalityIndex = $thresholdIndex;
        }

        # Is the personality at or below the threshold?
        if( $threshold >= $personalityValues[$thresholdIndex] )
        {
            # Yup
            $found = 1;
        }
        else
        {
            # Nope, keep looking
            $thresholdIndex--;
        }
    }

#print "thresholdIndex=[$thresholdIndex]\n";

    # Calculate the personality values to use
    my @personalityValuesSliced;
    if( $thresholdIndex < $#personalityValues )
    {
        # Calculate the index
        if( $thresholdIndex > MINTHRESHOLDIDX )
        {
            $thresholdIndex = min( (ceil($lastMaxPersonalityIndex / PVBUFFERPERCENTAGE) + 1),
                    $#personalityValues );
        }
        else
        {
            $thresholdIndex = max( MINTHRESHOLDIDX,
                    (ceil($lastMaxPersonalityIndex / PVBUFFERPERCENTAGE) + 1) );
        }

#print "Original length=[$#personalityValues]\n";
#print "Recalculated thresholdIndex=[$thresholdIndex]\n";


        # Slice the array
        my @pvCopy = @personalityValues;
        @personalityValuesSliced = @personalityValues[ 0 .. $thresholdIndex ];
#print "Spliced=[@personalityValuesSliced]\n";
    }
    else
    {
        # We couldn't shorten it at all!
        @personalityValuesSliced = @personalityValues;
    }

    # Return the reference to the sliced array
    return \@personalityValuesSliced;
}


# ===================================================================

