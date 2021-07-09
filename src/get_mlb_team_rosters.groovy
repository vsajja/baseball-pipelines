import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

@Field
def MLB_STATS_API_BASE_URL = 'https://statsapi.mlb.com/api/v1'

@Field
def MLB_SPORT_CODE = 1

@Field
def mlbTeams = []

node('master') {
    stage('GetTeams') {
        // get all artifacts from last successful build
        copyArtifacts(projectName: 'get-mlb-teams')

        def mlbTeamsJsonStr = readFile('mlb-teams.json')

        mlbTeams = new JsonSlurperClassic().parseText(mlbTeamsJsonStr)

        mlbTeams.each {
            println it
        }
    }

    stage('GetRosters') {
        mlbTeams.each { mlbTeam ->
            def mlbTeamId = mlbTeam['id']
            // get 40 man roster
            def roster = getMlbRoster(mlbTeamId)

            def fileName = "${mlbTeam['abbreviation']}-roster.json"
            println "Writing file: ${fileName}"
            writeFile file: fileName, text: new JsonBuilder(roster).toPrettyString()
        }
    }

    stage('ArchiveRosters') {
        mlbTeams.each { mlbTeam ->
            def artifactName = "${mlbTeam['abbreviation']}-roster.json"
            println "Archiving file: ${artifactName}"
            archiveArtifacts artifacts: artifactName, onlyIfSuccessful: true
        }
    }
}

def getMlbRoster(def mlbTeamId) {
    String jsonStr = "$MLB_STATS_API_BASE_URL/teams/${mlbTeamId}/roster?rosterType=40Man".toURL().text
    def jsonObj = new JsonSlurperClassic().parseText(jsonStr)
    def players = []

    players = jsonObj.roster.collect { player ->
        Integer mlbPlayerId = player['person']['id']
        Integer jerseyNumber = player['jerseyNumber'] != '' ? Integer.parseInt(player['jerseyNumber']) : null
        String playerLink = player['person']['link']

        // use the playerLink to get more player details
        def playerInfo = new JsonSlurperClassic().parseText(
                "https://statsapi.mlb.com/${playerLink}".toURL().text
        )

        String nameFirst = playerInfo['people']['useName'][0]
        String nameLast = playerInfo['people']['lastName'][0]
        String birthDate = playerInfo['people']['birthDate'][0]
        Integer age = playerInfo['people']['currentAge'][0]

        String birthCity = playerInfo['people']['birthCity'][0]
        String birthCountry = playerInfo['people']['birthCountry'][0]
        Integer heightFt = Integer.parseInt((playerInfo['people']['height'][0].split(' ')[0] - "'").trim())
        Integer heightInches = Integer.parseInt(playerInfo['people']['height'][0].split(' ')[1] - "\"".trim())
        Integer weight = playerInfo['people']['weight'][0]
        String position = playerInfo['people']['primaryPosition']['abbreviation'][0]
        String mlbDebutDate = playerInfo['people']['mlbDebutDate'][0]

        String pitchHand = playerInfo['people']['pitchHand']['code'][0]
        String bats = playerInfo['people']['batSide']['code'][0]

        return [
                'mlbPlayerId' : mlbPlayerId,
                'jerseyNumber': jerseyNumber,
                'nameFirst'   : nameFirst,
                'nameLast'    : nameLast,
                'birthDate'   : birthDate,
                'age'         : age,
                'birthCity'   : birthCity,
                'birthCountry': birthCountry,
                'heightFt'    : heightFt,
                'heightInches': heightInches,
                'weight'      : weight,
                'position'    : position,
                'mlbDebutDate': mlbDebutDate,
                'pitchHand'   : pitchHand,
                'bats'        : bats
        ]
    }

    return players
}