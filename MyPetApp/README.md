# Example: My Pet Android Client App

Here you can find the Android Client App that is created following the walkthrough instructionis.  

## Set Up API
To have the app up and running, you still need to connect to your AWS account to create the necessary cloud infrastructure. Follow the instructions to [Add an API](https://github.com/janeshenamazon/amplify-android#installing-the-aws-amplify-cli-amd-initializing-a-new-aws-amplify-project) to set up Amplify.

## Schema Used

The example app assumes the following GraphQL schema is used: 

```graphql
type Pet @model {
  id: ID!
  name: String!
  description: String
}
```
