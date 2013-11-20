package edu.snu.leader.discrete.simulator;

import java.util.List;

import org.apache.commons.lang.Validate;

import edu.snu.leader.discrete.behavior.Decision;
import edu.snu.leader.discrete.behavior.Decision.DecisionType;
import edu.snu.leader.discrete.utils.Reporter;

public class GautraisConflictDecisionCalculator implements
DecisionProbabilityCalculator
{
    /** The simulation state */
    private SimulationState _simState = null;

    private double _tauO = 0;

    private double _alphaC = 0;

    private double _gammaC = 0;

    private double _epsilonC = 0;

    private double _alphaF = 0;

    private double _betaF = 0;
    
    private double[] _followProbabilities = null;
    private double[] _cancelProbabilities = null;

    @Override
    public void initialize( SimulationState simState )
    {
        _simState = simState;

        String tauO = _simState.getProperties().getProperty( "tau-o" );
        Validate.notEmpty( tauO, "tau-o may not be empty" );
        _tauO = Double.parseDouble( tauO );

        String alphaF = _simState.getProperties().getProperty( "alpha-f" );
        Validate.notEmpty( alphaF, "alpha-f may not be empty" );
        _alphaF = Double.parseDouble( alphaF );

        String gammaC = _simState.getProperties().getProperty( "gamma-c" );
        Validate.notEmpty( gammaC, "gamma-c may not be empty" );
        _gammaC = Double.parseDouble( gammaC );

        String epsilonC = _simState.getProperties().getProperty( "epsilon-c" );
        Validate.notEmpty( epsilonC, "epsilon-c may not be empty" );
        _epsilonC = Double.parseDouble( epsilonC );

        String alphaC = _simState.getProperties().getProperty( "alpha-c" );
        Validate.notEmpty( alphaC, "alpha-c may not be empty" );
        _alphaC = Double.parseDouble( alphaC );

        String betaF = _simState.getProperties().getProperty( "beta-f" );
        Validate.notEmpty( betaF, "beta-f may not be empty" );
        _betaF = Double.parseDouble( betaF );
        
        // add gautrais info to root directory path
        _simState.setRootDirectory( Reporter.ROOT_DIRECTORY + "GautraisValues" );
    }
    
    /**
     * Returns an array of all the possible follow probabilities. Will be null if pre-generation was not specified in properties file. 
     *
     * @return
     */
    public double[] getPreCalculatedFollowProbabilities(){
        return _followProbabilities;
    }
    
    /**
     * Returns an array of all the possible cancel probabilities. Will be null if pre-generation was not specified in properties file. 
     *
     * @return
     */
    public double[] getPreCalculatedCancelProbabilities(){
        return _cancelProbabilities;
    }

    @Override
    public void calcInitiateProb( Decision decision )
    {
        double conflict = calculateConflict( decision );
        //just conflict for initiate
        decision.setProbability(( 1 / kValue( conflict ) ) *( 1 / _tauO ));
    }

    @Override
    public void calcFollowProb( Decision decision )
    {
        double conflict = calculateConflict( decision );
        double tauR = 0;
        // total number neighbors
        int N = 0;
        // followers
        int r = 0;

        Agent agent = decision.getAgent();

        // calculate r followers and N total
        List<Agent> neighbors = agent.getNearestNeighbors();
        N = neighbors.size();
        for( int i = 0; i < neighbors.size(); i++ )
        {
            // N++;
            if( agent.getObservedGroupHistory().get( neighbors.get( i ).getId() ).groupId == ( decision.getLeader().getGroup().getId() ) )
            {
                r++;
            }
        }

        // calculate tauR
        tauR = _alphaF + ( ( _betaF * ( N - r ) ) / r );
        //1-conflict for follow
        tauR *= 1 / kValue( 1 - conflict );
        decision.setProbability( 1 / tauR );
    }

    @Override
    public void calcCancelProb( Decision decision )
    {
        double conflict = calculateConflict( decision );
        double Cr = 0;
        // followers
        int r = 1;

        Agent agent = decision.getAgent();

        // calculate r followers
        List<Agent> neighbors = agent.getNearestNeighbors();
        for( int i = 0; i < neighbors.size(); i++ )
        {
            if( agent.getObservedGroupHistory().get( neighbors.get( i ).getId() ).groupId.equals( agent.getGroup().getId() ) )
            {
                r++;
            }
        }

        // calculate Cr
        Cr = _alphaC / ( 1 + ( Math.pow( r / _gammaC, _epsilonC ) ) );
        //1-conflict for cancel
        Cr *= kValue( 1 - conflict );
        decision.setProbability( Cr );

    }
    
    /**
     * Calculate the k value for conflict
     * 
     * @param decision The decision that the k value is calculated for
     * @return The k value
     */
    private double kValue( double conflict )
    {
        // k = 2 * conflict
        double k = 2 * conflict ;
        return k;
    }
    
    private double calculateConflict(Decision decision){
        // Ci = p^.5 * |di - dI|^.5
        float p = decision.getAgent().getPersonalityTrait().getPersonality();
        // agent's preferred direction
        double di = decision.getAgent().getPreferredDirection();
        // leader's preferred direction
        double dI = decision.getLeader().getPreferredDirection();
        // difference in preferred directions
        double dir_diff = 0;
        // if decision is to follow calculate, otherwise dir_diff = 0
        if( decision.getDecisionType().equals( DecisionType.FOLLOW ) )
        {
            dir_diff = Math.abs( di - dI );
        }
        // the formula
        double Ci = Math.pow( p, .5 ) * Math.pow( dir_diff, .5 );
        return Ci;
    }
}