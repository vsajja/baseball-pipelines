import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic
import groovy.transform.Field

@Field
def MLB_STATS_API_BASE_URL = 'https://statsapi.mlb.com/api/v1'

@Field
def MLB_SPORT_CODE = 1

// league ids
@Field
def int AL = 103
@Field
def int NL = 104

// AL division ids
@Field
def AL_EAST = 201
@Field
def AL_CENTRAL = 202
@Field
def AL_WEST = 200

@Field
def NL_EAST = 204
@Field
def NL_CENTRAL = 205
@Field
def NL_WEST = 203

@Field
def mlbTeams = []

node('master') {
    stage('GetTeams') {
        mlbTeams = getMlbTeams()
        mlbTeams.each {
            println it
        }
    }

    stage('SaveTeams') {
        writeFile file: 'mlbTeams.json', text: new JsonBuilder(mlbTeams).toPrettyString()
    }

    stage('ArchiveTeams') {
        archiveArtifacts artifacts: 'mlbTeams.json', onlyIfSuccessful: true
    }
}

def getMlbTeams() {
    String jsonStr = "$MLB_STATS_API_BASE_URL/teams?sportCode=${MLB_SPORT_CODE}".toURL().text

    def teamsObj = new JsonSlurperClassic().parseText(jsonStr)

    def mlbTeams = teamsObj['teams'].findAll {
        it['league'] &&
                it['division'] &&
                it['sport'] &&
                it['sport']['name'] != 'National Basketball Association'
    }.collect { team ->
        return [
                'team'           : team['name'],
                'abbreviation'   : team['abbreviation'],
                'division'       : team['division']['name'],
                'league'         : team['league']['name'],
                'level'          : team['sport']['name'],
                'firstYearOfPlay': Integer.parseInt(team['firstYearOfPlay']),
                'divisionId'     : team['division']['id'],
                'leagueId'       : team['league']['id'],
                'id'             : team['id']
        ]
    }.findAll { it.level == 'Major League Baseball' }

    assert mlbTeams.size() == 30

    return mlbTeams
}
