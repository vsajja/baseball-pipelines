import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

@Field
def mlbTeams = []

@Field
def mlbPlayers = []

@Field
def catchers = []

@Field
def hittingStats = [:]

node('master') {
    stage('CleanWs') {
        cleanWs()
    }

    stage('GetTeams') {
        copyArtifacts filter: 'mlb-teams.json', fingerprintArtifacts: true, projectName: 'get-mlb-teams'

        def mlbTeamsJsonStr = readFile('mlb-teams.json')

        mlbTeams = new JsonSlurperClassic().parseText(mlbTeamsJsonStr)

        mlbTeams.each {
            println it
        }
    }

    stage('GetPlayers') {
        copyArtifacts filter: '*.json', fingerprintArtifacts: true, projectName: 'get-mlb-team-rosters'
        copyArtifacts filter: '*.json', fingerprintArtifacts: true, projectName: 'get-mlb-player-hitting-stats'

        mlbTeams.each { mlbTeam ->
            def teamCode = mlbTeam['abbreviation']

            def teamRosterJsonStr = readFile("${teamCode}-roster.json")
            def teamHittingStatsJsonStr = readFile("${teamCode}-hitting-stats.json")

            def teamRoster = new JsonSlurperClassic().parseText(teamRosterJsonStr)
            def teamHittingStats = new JsonSlurperClassic().parseText(teamHittingStatsJsonStr)

            mlbPlayers.addAll(teamRoster)
            hittingStats.putAll(teamHittingStats)
        }

        println mlbPlayers.collect { it['position'] }.unique()
    }

    stage('GetCatchers') {
        catchers = mlbPlayers.findAll { it['position'] == 'C' }

        def ratedCatchers = getRatedCatchers(catchers)

        // catchers rated by their roto score
        println ratedCatchers.collect {
            return "${it['nameFirst']} ${it['nameLast']} ${it['rotoScore']} ${it['zScore']}"
        }.join('\n')
    }
}

@NonCPS
def getRatedCatchers(catchers) {
    // add roto score
    catchers = catchers.collect { catcher ->
        println "${catcher['nameFirst']} ${catcher['nameLast']}"
        assert hittingStats.get(catcher['mlbPlayerId'].toString()) != null

        // current year
        def seasonHittingStats = hittingStats.get(catcher['mlbPlayerId'].toString()).find {
            it['season'] == 2021
        }

        catcher.put('seasonHittingStats', seasonHittingStats)
        catcher.put('rotoScore', getRotoScore(seasonHittingStats))

        return catcher
    }.sort { a, b -> b.rotoScore <=> a.rotoScore }

    // variables to calculate standard deviation
    def rotoScores = catchers.collect { it['rotoScore'] }
    def rotoScoresSquared = rotoScores.collect { it * it }
    def rotoScoresSquaredSum = rotoScoresSquared.sum()

    def populationSize = rotoScores.size()

    def totalRotoScoresSquared = rotoScores.sum() * rotoScores.sum()

    def standardDeviation = Math.sqrt(
            (((populationSize * rotoScoresSquaredSum) - totalRotoScoresSquared) / (populationSize * populationSize)).doubleValue()
    )
    def mean = rotoScores.sum() / rotoScores.size()

    // add z-score
    catchers = catchers.collect { catcher ->
        println "${catcher['nameFirst']} ${catcher['nameLast']}"

        def zScore = ((catcher['rotoScore'] - mean) / standardDeviation)
        println zScore

        catcher.put('zScore', zScore)

        return catcher
    }.sort { a, b -> b.zScore <=> a.zScore }

    return catchers
}

@NonCPS
def getRotoScore(seasonHittingStats) {
    if (seasonHittingStats == null) {
        return 0
    }

    def season = seasonHittingStats['season']

    assert season == 2021

    def atBats = seasonHittingStats['atBats']

    def runs = seasonHittingStats['runs']
    def homeRuns = seasonHittingStats['homeRuns']
    def rbis = seasonHittingStats['rbis']
    def stolenBases = seasonHittingStats['stolenBases']
    def avg = seasonHittingStats['avg']

    return (avg * atBats) + (3 * runs) + (12 * homeRuns) + (3.5 * rbis) + (6 * stolenBases)

//    def playerRanking =  (runs + homeRuns + rbis + stolenBases + avg)
//
//
////    seasonHittingStats['slg']
////    seasonHittingStats['obp']
////    seasonHittingStats['ops']
////
////    seasonHittingStats['strikeOuts']
////    seasonHittingStats['doubles']
////    seasonHittingStats['triples']
////    seasonHittingStats['hitByPitch']
////    seasonHittingStats['sacBunts']
////    seasonHittingStats['totalBases']
////    seasonHittingStats['games']
////    seasonHittingStats['teamNumber']
////    seasonHittingStats['airOuts']
////    seasonHittingStats['sacFlies']
////    seasonHittingStats['caughtStealing']
////    seasonHittingStats['baseOnBalls']
////    seasonHittingStats['numberOfPitches']
////    seasonHittingStats['hits']
////    seasonHittingStats['plateAppearances']
////    seasonHittingStats['groundOuts']
////    seasonHittingStats['leftOnBase']
////    seasonHittingStats['babip']
////    seasonHittingStats['intentionalWalks']
////    seasonHittingStats['groundIntoDoublePlay']
}


