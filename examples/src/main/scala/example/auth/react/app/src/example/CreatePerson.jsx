import React from "react";

class CreatePerson extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            isSuccess: false
        };
    }

    sendCreateRequest = () =>  {this.sendRequest("POST")};

    sendUpdateRequest = () => {this.sendRequest("PUT")};

    sendPatchRequest = () => {this.sendRequest("PATCH")};

    sendRequest = (method) => {
        setTimeout(async () => {
            const keycloak = await this.props.keycloak;

            const response =  await fetch("http://localhost:9003/person", {
                method: method,
                headers: {
                    "Authorization" : `Bearer ${keycloak.token}`
                }
            });

            if(response.ok) {
                alert("Action successful")
            } else {
                alert("Action failed")
            }

        }, 3000);
    };

    render() {
        return <div>
            <button onClick={this.sendCreateRequest}>Create Person</button>
            <br/>
            <button onClick={this.sendUpdateRequest}>Update Person</button>
            <br/>
            <button onClick={this.sendPatchRequest}>Not permitted Route</button>
        </div>
    }
}

export default CreatePerson