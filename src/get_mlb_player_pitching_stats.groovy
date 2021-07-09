import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

@Field
def MLB_STATS_API_BASE_URL = 'https://statsapi.mlb.com/api/v1'

@Field
def MLB_SPORT_CODE = 1

@Field
def mlbTeams = []

@Field
def roster = []

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

    stage('GetPitchingStats') {
        // get all artifacts from last successful build
        copyArtifacts projectName: 'get-mlb-team-rosters'

        mlbTeams.each { mlbTeam ->
            def teamCode = mlbTeam['abbreviation']

            def rosterJsonStr = readFile("${teamCode}-roster.json")

            roster = new JsonSlurperClassic().parseText(rosterJsonStr)

            def fileName = "${teamCode}-pitching-stats.json"
            println "Writing file: ${fileName}"

            def pitchingStats = [:]

            roster.each { player ->
                def stats = getPlayerPitchingStats(player)

                println stats.collect {
                    def season = it['season']

                    def teamId = it['mlbTeamId']

                    def team = mlbTeams.find {
                        it['id'] == teamId
                    }

                    def games = it['games']
                    def gamesStarted = it['gamesStarted']
                    def wins = it['wins']
                    def losses = it['losses']
                    def saves = it['saves']
                    def blownSaves = it['blownSaves']
                    def holds = it['holds']
                    def completeGames = it['completeGames']
                    def shutouts = it['shutouts']
                    def inningsPitched = it['inningsPitched']
                    def hits = it['hits']
                    def runs = it['runs']
                    def earnedRuns = it['earnedRuns']
                    def homeRuns = it['homeRuns']
                    def baseOnBalls = it['baseOnBalls']
                    def strikeOuts = it['strikeOuts']
                    def era = it['era']
                    def whip = it['whip']
                    def avg = it['avg']

                    return "${season} ${team != null ? team['abbreviation'] : null} " +
                            "${games}G ${gamesStarted}GS " +
                            "${inningsPitched}IP " +
                            "${wins}W " +
                            "${saves}SV " +
                            "${strikeOuts}K " +
                            "${era}ERA " +
                            "${whip}WIHP ${avg}BAA"
                }.join('\n')

                pitchingStats.put(player['mlbPlayerId'], stats)
            }

            writeFile file: fileName, text: new JsonBuilder(pitchingStats).toPrettyString()
        }
    }

    stage('ArchiveHittingStats') {
        archiveArtifacts artifacts: '**-pitching-stats.json', onlyIfSuccessful: true
    }
}

def getPlayerPitchingStats(def player) {
    Integer startYear = null
    Integer endYear = Integer.valueOf(Calendar.getInstance().get(Calendar.YEAR));

    def pitchingStats = []

    def mlbDebutDate = player['mlbDebutDate']

    // does this player have any major league stats?
    if (mlbDebutDate != null) {
        startYear = Integer.parseInt(mlbDebutDate.split('-')[0])

        println player
        println "Start Year: ${startYear}"
        println "End Year: ${endYear}"

        for (year in (startYear..endYear)) {
            def jsonStr = "$MLB_STATS_API_BASE_URL/people/${player['mlbPlayerId']}?hydrate=stats(group=[pitching],type=season,season=${year})".toURL().text
            def jsonObj = new JsonSlurperClassic().parseText(jsonStr)

            // some players (pitchers) don't have hitting stats
            if (jsonObj['people']['stats'][0] != null) {
                // println new JsonBuilder(jsonObj['people']).toString()

                def splits = jsonObj['people']['stats'][0]['splits'][0]

                def stats = []

                // split season (player on multiple teams)
                if (splits.size() > 1) {
                    splits.eachWithIndex { splitSeasonStats, teamNumber ->
                        splitSeasonStats.putAt('teamNumber', teamNumber)
                        // println splitSeasonStats.toString()
                        stats.add(splitSeasonStats)
                    }
                } else {
                    stats.add(splits[0])
                }

                stats.each { stat ->
                    def seasonStats = stat['stat']
                    def team = stat['team']
                    def teamNumber = stat['teamNumber'] != null ? stat['teamNumber'] : 0
                    def season = Integer.parseInt(stat['season'])

                    def pitchingStatLine = parseSeasonPitchingStats(seasonStats)

                    // println seasonStats.toString()

                    // set the team the player played for this season (player can be on multiple teams)
                    if (team != null) {
                        def mlbTeamId = stat['team']['id']
                        pitchingStatLine['mlbTeamId'] = mlbTeamId
                    } else {
                        // combined stats for entire season, no team
                    }

                    // set mlb player id & season
                    pitchingStatLine['mlbPlayerId'] = player['mlbPlayerId']
                    pitchingStatLine['season'] = season
                    pitchingStatLine['teamNumber'] = teamNumber

                    // println "adding: "
                    // println hittingStatLine.toString()

                    pitchingStats.add(pitchingStatLine)
                }
            }
        }
    }

    return pitchingStats
}

def parseSeasonPitchingStats(seasonStats) {
    println seasonStats.toString()

    def games = seasonStats['gamesPlayed']
    def gamesStarted = seasonStats['gamesStarted']
    def wins = seasonStats['wins']
    def losses = seasonStats['losses']
    def saves = seasonStats['saves']
    def blownSaves = seasonStats['blownSaves']
    def holds = seasonStats['holds']
    def completeGames = seasonStats['completeGames']
    def shutouts = seasonStats['shutouts']

    def inningsPitched = seasonStats['inningsPitched']
    def hits = seasonStats['hits']
    def runs = seasonStats['runs']
    def earnedRuns = seasonStats['earnedRuns']
    def homeRuns = seasonStats['homeRuns']
    def baseOnBalls = seasonStats['baseOnBalls']
    def strikeOuts = seasonStats['strikeOuts']
    def era = seasonStats['era']
    def whip = seasonStats['whip']
    def avg = seasonStats['avg']

    return [
            games         : seasonStats['gamesPlayed'],
            gamesStarted  : seasonStats['gamesStarted'],
            wins          : seasonStats['wins'],
            losses        : seasonStats['losses'],
            saves         : seasonStats['saves'],
            blownSaves    : seasonStats['blownSaves'],
            holds         : seasonStats['holds'],
            completeGames : seasonStats['completeGames'],
            shutouts      : seasonStats['shutouts'],
            inningsPitched: seasonStats['inningsPitched'],
            hits          : seasonStats['hits'],
            runs          : seasonStats['runs'],
            earnedRuns    : seasonStats['earnedRuns'],
            homeRuns      : seasonStats['homeRuns'],
            baseOnBalls   : seasonStats['baseOnBalls'],
            strikeOuts    : seasonStats['strikeOuts'],
            era           : seasonStats['era'],
            whip          : seasonStats['whip'],
            avg           : seasonStats['avg']
    ]

    // TODO: more stats
//    def gamesFinished = seasonStats['gamesFinished']
//    def homeRunsPer9 = seasonStats['homeRunsPer9']
//    def triples = seasonStats['triples']
//    def catchersInterference = seasonStats['catchersInterference']
//    def saveOpportunities = seasonStats['saveOpportunities']
//    def strikeoutsPer9Inn = seasonStats['strikeoutsPer9Inn']
//    def inheritedRunnersScored = seasonStats['inheritedRunnersScored']
//    def totalBases = seasonStats['totalBases']
//    def airOuts = seasonStats['airOuts']
//    def sacFlies = seasonStats['sacFlies']
//    def strikePercentage = seasonStats['strikePercentage']
//    def balks = seasonStats['balks']
//    def numberOfPitches = seasonStats['numberOfPitches']
//    def slg = seasonStats['slg']
//    def groundOuts = seasonStats['groundOuts']
//    def ops = seasonStats['ops']
//    def stolenBasePercentage = seasonStats['stolenBasePercentage']
//    def doubles = seasonStats['doubles']
//    def wildPitches = seasonStats['wildPitches']
//    def groundOutsToAirouts = seasonStats['groundOutsToAirouts']
//    def intentionalWalks = seasonStats['intentionalWalks']
//    def groundIntoDoublePlay = seasonStats['groundIntoDoublePlay']
//    def inheritedRunners = seasonStats['inheritedRunners']
//    def walksPer9Inn = seasonStats['walksPer9Inn']
//    def hitByPitch = seasonStats['hitByPitch']
//    def sacBunts = seasonStats['sacBunts']
//    def outs = seasonStats['outs']
//    def stolenBases = seasonStats['stolenBases']
//    def strikes = seasonStats['strikes']
//    def atBats = seasonStats['atBats']
//    def caughtStealing = seasonStats['caughtStealing']
//    def gamesPitched = seasonStats['gamesPitched']
//    def hitBatsmen = seasonStats['hitBatsmen']
//    def strikeoutWalkRatio = seasonStats['strikeoutWalkRatio']
//    def runsScoredPer9 = seasonStats['runsScoredPer9']
//    def hitsPer9Inn = seasonStats['hitsPer9Inn']
//    def battersFaced = seasonStats['battersFaced']
//    def pitchesPerInning = seasonStats['pitchesPerInning']
//    def obp = seasonStats['obp']
//    def winPercentage = seasonStats['winPercentage']

    return seasonStats
}
