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
        mlbTeams = getMlbTeams()
        mlbTeams.each {
            println it
        }
    }

    stage('SaveTeams') {
        writeFile file: 'mlb-teams.json', text: new JsonBuilder(mlbTeams).toPrettyString()
    }

    stage('ArchiveTeams') {
        archiveArtifacts artifacts: 'mlb-teams.json', onlyIfSuccessful: true
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
