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
        //        [P, C, 3B, CF, 2B, RF, 1B, SS, LF, OF, DH]

        catchers = mlbPlayers.findAll {it['position'] == 'C'}

        catchers.each { catcher ->
            println "${catcher['nameFirst']} ${catcher['nameLast']}"
            assert hittingStats.get(catcher['mlbPlayerId'].toString()) != null
            println hittingStats.get(catcher['mlbPlayerId'].toString())
        }
    }
}

//def ratePlayer(player, hittingStats) {
//    println "Rating: ${player}"
//
//    def currentYear = 2021
//
//    // current year
//    def seasonHittingStats = hittingStats.find {
//        it['season'] == 2021
//    }
//
//    assert seasonHittingStats != null
//
//    def season = seasonHittingStats['season']
//
//    def atBats = seasonHittingStats['atBats']
//
//    def runs = seasonHittingStats['runs']
//    def homeRuns = seasonHittingStats['homeRuns']
//    def rbis = seasonHittingStats['rbis']
//    def stolenBases = seasonHittingStats['stolenBases']
//    def avg = seasonHittingStats['avg']
//
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
//}

