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

    stage('GetHittingStats') {
        // get all artifacts from last successful build
        copyArtifacts projectName: 'get-mlb-team-rosters'

        mlbTeams.each { mlbTeam ->
            def teamCode = mlbTeam['abbreviation']

            def rosterJsonStr = readFile("${teamCode}-roster.json")

            roster = new JsonSlurperClassic().parseText(rosterJsonStr)

            def fileName = "${teamCode}-hitting-stats.json"
            println "Writing file: ${fileName}"

            def hittingStats = [:]

            roster.each { player ->
                def stats = getPlayerHittingStats(player)

                println stats.collect {

                    def season = it['season']

                    def teamId = it['mlbTeamId']

                    def team = mlbTeams.find {
                        it['id'] == teamId
                    }

                    def games = it['games']
                    def atBats = it['atBats']
                    def runs = it['runs']
                    def homeRuns = it['homeRuns']
                    def rbis = it['rbis']
                    def stolenBases = it['stolenBases']
                    def avg = it['avg']
                    def obp = it['obp']
                    def slg = it['slg']
                    def ops = it['ops']

                    return "${season} ${team != null ? team['abbreviation'] : null} ${games}G  ${atBats}AB ${runs}R ${homeRuns}HR ${rbis}RBI ${stolenBases}SB ${avg}/${obp}/${slg}/${ops}"
                }.join('\n')

                hittingStats.put(player['mlbPlayerId'], stats)
            }

            writeFile file: fileName, text: new JsonBuilder(hittingStats).toPrettyString()
        }
    }

    stage('ArchiveHittingStats') {
        archiveArtifacts artifacts: '**-hitting-stats.json', onlyIfSuccessful: true
    }
}

def getPlayerHittingStats(def player) {
    Integer startYear = null
    Integer endYear = Integer.valueOf(Calendar.getInstance().get(Calendar.YEAR));

    def hittingStats = []

    def mlbDebutDate = player['mlbDebutDate']

    // does this player have any major league stats?
    if (mlbDebutDate != null) {
        startYear = Integer.parseInt(mlbDebutDate.split('-')[0])

        println player
        println "Start Year: ${startYear}"
        println "End Year: ${endYear}"

        for (year in (startYear..endYear)) {
            def jsonStr = "$MLB_STATS_API_BASE_URL/people/${player['mlbPlayerId']}?hydrate=stats(group=[hitting],type=season,season=${year})".toURL().text
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

                    def hittingStatLine = parseSeasonHittingStats(seasonStats)

                    // println seasonStats.toString()

                    // set the team the player played for this season (player can be on multiple teams)
                    if (team != null) {
                        def mlbTeamId = stat['team']['id']
                        hittingStatLine['mlbTeamId'] = mlbTeamId
                    } else {
                        // combined stats for entire season, no team
                    }

                    // set mlb player id & season
                    hittingStatLine['mlbPlayerId'] = player['mlbPlayerId']
                    hittingStatLine['season'] = season
                    hittingStatLine['teamNumber'] = teamNumber

                    // println "adding: "
                    // println hittingStatLine.toString()

                    hittingStats.add(hittingStatLine)
                }
            }
        }
    }

    return hittingStats
}

def parseSeasonHittingStats(seasonStats) {
    def games = seasonStats['gamesPlayed']
    def atBats = seasonStats['atBats']
    def runs = seasonStats['runs']
    def homeRuns = seasonStats['homeRuns']
    def rbis = seasonStats['rbi']
    def stolenBases = seasonStats['stolenBases']
    def caughtStealing = seasonStats['caughtStealing']
    def avg = Double.parseDouble(seasonStats['avg'])
    def obp = Double.parseDouble(seasonStats['obp'])
    def slg = Double.parseDouble(seasonStats['slg'])
    def ops = Double.parseDouble(seasonStats['ops'])
    def doubles = seasonStats['doubles']
    def triples = seasonStats['triples']
    def hits = seasonStats['hits']
    def strikeOuts = seasonStats['strikeOuts']
    def baseOnBalls = seasonStats['baseOnBalls']
    def intentionalWalks = seasonStats['intentionalWalks']
    def groundOuts = seasonStats['groundOuts']
    def airOuts = seasonStats['airOuts']
    def hitByPitch = seasonStats['hitByPitch']
    def groundIntoDoublePlay = seasonStats['groundIntoDoublePlay']
    def numberOfPitches = seasonStats['numberOfPitches']
    def plateAppearances = seasonStats['plateAppearances']
    def totalBases = seasonStats['totalBases']
    def leftOnBase = seasonStats['leftOnBase']
    def sacBunts = seasonStats['sacBunts']
    def sacFlies = seasonStats['sacFlies']
    def babip = seasonStats['babip'] != '.---' ? Double.parseDouble(seasonStats['babip']) : null

    return [
            'games'               : games,
            'atBats'              : atBats,
            'runs'                : runs,
            'homeRuns'            : homeRuns,
            'rbis'                : rbis,
            'stolenBases'         : stolenBases,
            'caughtStealing'      : caughtStealing,
            'avg'                 : avg,
            'obp'                 : obp,
            'slg'                 : slg,
            'ops'                 : ops,
            'doubles'             : doubles,
            'triples'             : triples,
            'hits'                : hits,
            'strikeOuts'          : strikeOuts,
            'baseOnBalls'         : baseOnBalls,
            'intentionalWalks'    : intentionalWalks,
            'groundOuts'          : groundOuts,
            'airOuts'             : airOuts,
            'hitByPitch'          : hitByPitch,
            'groundIntoDoublePlay': groundIntoDoublePlay,
            'numberOfPitches'     : numberOfPitches,
            'plateAppearances'    : plateAppearances,
            'totalBases'          : totalBases,
            'leftOnBase'          : leftOnBase,
            'sacBunts'            : sacBunts,
            'sacFlies'            : sacFlies,
            'babip'               : babip
    ]
}
